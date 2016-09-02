package com.lzy.imagepickerdemo;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.lzy.imagepicker.ImagePicker;
import com.lzy.imagepicker.bean.ImageItem;
import com.lzy.imagepicker.ui.ImageGridActivity;
import com.lzy.imagepickerdemo.imageloader.ImageLoader;
import com.lzy.imagepickerdemo.wxdemo.WxDemoActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * ================================================
 * 作    者：jeasonlzy（廖子尧 Github地址：https://github.com/jeasonlzy0216
 * 版    本：1.0
 * 创建日期：2016/5/19
 * 描    述：
 * 修订历史：
 * ================================================
 */
public class ImagePickerActivity extends AppCompatActivity implements View.OnClickListener {

    private ImagePicker imagePicker;
    private GridView gridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_picker);

        imagePicker = ImagePicker.getInstance();
        imagePicker.setImageLoader(ImageLoader.getInstance());

        Button btn_open_gallery = (Button) findViewById(R.id.btn_open_gallery);
        btn_open_gallery.setOnClickListener(this);
        Button btn_wxDemo = (Button) findViewById(R.id.btn_wxDemo);
        btn_wxDemo.setOnClickListener(this);

        gridView = (GridView) findViewById(R.id.gridview);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_open_gallery:
                Intent intent = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent, 100);
                break;
            case R.id.btn_wxDemo:
                startActivity(new Intent(this, WxDemoActivity.class));
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == ImagePicker.RESULT_CODE_ITEMS) {
            if (data != null && requestCode == 100) {
                ArrayList<ImageItem> images = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
                MyAdapter adapter = new MyAdapter(images);
                gridView.setAdapter(adapter);
            } else {
                Toast.makeText(this, "没有数 据", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class MyAdapter extends BaseAdapter {

        private List<ImageItem> items;

        public MyAdapter(List<ImageItem> items) {
            this.items = items;
        }

        public void setData(List<ImageItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public ImageItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            int size = gridView.getWidth() / 3;
            if (convertView == null) {
                imageView = new ImageView(ImagePickerActivity.this);
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size);
                imageView.setLayoutParams(params);
                imageView.setBackgroundColor(Color.parseColor("#88888888"));
            } else {
                imageView = (ImageView) convertView;
            }
            imagePicker.getImageLoader().displayImage(ImagePickerActivity.this, getItem(position).path, imageView, size, size);
            return imageView;
        }
    }
}
