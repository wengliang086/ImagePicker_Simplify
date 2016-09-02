package com.lzy.imagepickerdemo.imageloader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 图片加载类
 * Created by Administrator on 2016/6/27.
 */
public class ImageLoader implements com.lzy.imagepicker.loader.ImageLoader {

    private static ImageLoader mInstance;

    /**
     * 图片缓存的核心对象
     */
    private LruCache<String, Bitmap> mLruCache;

    /**
     * 线程池
     */
    private ExecutorService mThreadPool;
    private static final int DEFAULT_THREAD_COUNT = 5;

    /**
     * 队列的调度方式
     */
    private Type mType = Type.FIFO;
    /**
     * 任务调度队列
     */
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;

    /**
     * UI线程中的Handler
     */
    private Handler mUIHandler;

    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);
    private Semaphore mSemaphoreThreadPool;

    @Override
    public void displayImage(Activity activity, String path, ImageView imageView, int width, int height) {
        loadImage(path, imageView);
    }

    @Override
    public void clearMemoryCache() {

    }

    public enum Type {
        FIFO, LIFO;
    }

    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    /**
     * 初始化
     *
     * @param threadCount
     * @param type
     */
    private void init(int threadCount, Type type) {
        // 后台轮询线程
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        // 线程池去取出一个任务进行执行
                        try {
                            mSemaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                        }
                        mThreadPool.execute(getTask());
                    }
                };
                // 释放一个信号量
                mSemaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };
        mSemaphoreThreadPool = new Semaphore(threadCount);
        mPoolThread.start();

        // 获取我们应用的最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };
        // 创建线程池
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<>();
        mType = type;
    }

    private Runnable getTask() {
        if (mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        } else if (mType == Type.LIFO) {
            return mTaskQueue.removeLast();
        }
        return null;
    }

    public static ImageLoader getInstance() {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }
        return mInstance;
    }

    public void loadImage(final String path, final ImageView imageView) {
        imageView.setTag(path);
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    // 获得获取的图片，为imageView设置图片
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imageView1 = holder.imageView;
                    String path = holder.path;
                    if (path.equals(imageView1.getTag().toString())) {
                        imageView1.setImageBitmap(bm);
                    }
                }
            };
        }

        Bitmap bitmap = mLruCache.get(path);
        if (bitmap != null) {
            refreshBitmap(path, imageView, bitmap);
        } else {
            addTask(new Runnable() {
                @Override
                public void run() {
                    // 获取图片
                    // 压缩图片
                    ImageSize size = getImageViewSize(imageView);
                    Bitmap bm = decodeSampledBitmapFromPath(path, size.width, size.height);
                    // 把图片加入缓存
                    addBitmapToLruCache(path, bm);

                    refreshBitmap(path, imageView, bm);
                    mSemaphoreThreadPool.release();
                }
            });
        }
    }

    private void refreshBitmap(String path, ImageView imageView, Bitmap bitmap) {
        Message msg = new Message();
        ImgBeanHolder holder = new ImgBeanHolder();
        holder.bitmap = bitmap;
        holder.imageView = imageView;
        holder.path = path;
        msg.obj = holder;
        mUIHandler.sendMessage(msg);
    }

    private void addBitmapToLruCache(String path, Bitmap bm) {
        if (mLruCache.get(path) == null) {
            mLruCache.put(path, bm);
        }
    }

    /**
     * 根据图片需要显示的宽、高对图片进行压缩
     *
     * @param path
     * @param width
     * @param height
     * @return
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int width, int height) {
        // 获取图片的宽和高，并不把图片加载到内存
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, width, height);
        // 使用得到的InSampleSize再次解析图片
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        return bitmap;
    }

    /**
     * 根据需求的宽和高以及图片实际的宽和高计算SampleSize
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if (width > reqWidth || height > reqHeight) {
            int widthRadio = Math.round(width * 1.0f / reqWidth);
            int heightRadio = Math.round(height * 1.0f / reqHeight);
            inSampleSize = Math.max(widthRadio, heightRadio);
        }
        return inSampleSize;
    }

    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize size = new ImageSize();

        DisplayMetrics displayMetrics = imageView.getResources().getDisplayMetrics();

        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        int width = imageView.getWidth();// 获取imageView的实际宽度
        if (width <= 0) {
            width = lp.width;// 获取imageView在layout中声明的宽度
        }
        if (width <= 0) {
            width = imageView.getMaxWidth();// 检查最大值
        }
        if (width <= 0) {
            width = displayMetrics.widthPixels;
        }

        int height = imageView.getWidth();// 获取imageView的实际宽度
        if (height <= 0) {
            height = lp.height;// 获取imageView在layout中声明的宽度
        }
        if (height <= 0) {
            height = imageView.getMaxHeight();// 检查最大值
        }
        if (height <= 0) {
            height = displayMetrics.heightPixels;
        }
        size.width = width;
        size.height = height;
        return size;
    }

    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);
        if (mPoolThreadHandler == null) {
            try {
                mSemaphorePoolThreadHandler.acquire();
            } catch (InterruptedException e) {
            }
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    class ImgBeanHolder {
        Bitmap bitmap;
        String path;
        ImageView imageView;
    }

    class ImageSize {
        int width;
        int height;
    }
}
