package com.hjsmallfly.syllabus.activities;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.hjsmallfly.syllabus.adapters.SyllabusAdapter;
import com.hjsmallfly.syllabus.helpers.ColorHelper;
import com.hjsmallfly.syllabus.helpers.FileOperation;
import com.hjsmallfly.syllabus.helpers.LessonPullTask;
import com.hjsmallfly.syllabus.helpers.StringDataHelper;
import com.hjsmallfly.syllabus.helpers.WebApi;
import com.hjsmallfly.syllabus.interfaces.LessonHandler;
import com.hjsmallfly.syllabus.interfaces.TokenGetter;
import com.hjsmallfly.syllabus.parsers.ClassParser;
import com.hjsmallfly.syllabus.syllabus.Lesson;
import com.hjsmallfly.syllabus.syllabus.R;
import java.io.File;
import java.util.HashMap;


public class SyllabusActivity extends AppCompatActivity implements LessonHandler, TokenGetter {

    public static Lesson clicked_lesson;

    public static final String DEFAULT_SYLLABUS_FILE = "default_syllabus";

    public static final String WALL_PAPER_FILE_NAME = "syllabus_wallpaper.jpeg";

    public static final String WALL_PAPER_FILE_TEMP = "temp_pic_file.jpg";

    public static final String COLOR_FILE_NAME = "syllabus_text_color";

    private static final int PICK_PHOTO_FROM_GALLERY = 1; // 从相册中选择
    private static final int CROP_PHOTO_REQUEST = 2; // 结果

    private RecyclerView syllabus_view;
    private SyllabusAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private TextView weekend_text;
    private Button show_oa_button;
//    private TextView info_text;

    private Bitmap wall_paper;

    private void setupViews(){
        // 设置 RecyclerView
        syllabus_view = (RecyclerView) findViewById(R.id.my_recycler_view);
        mLayoutManager = new GridLayoutManager(this, 6, RecyclerView.VERTICAL, false);  // 不管周末的课程先
        GridLayoutManager gridLayoutManager = (GridLayoutManager) mLayoutManager;
        syllabus_view.setLayoutManager(gridLayoutManager);
        mAdapter = new SyllabusAdapter(MainActivity.weekdays_syllabus_data, this);
        mAdapter.set_color(ColorHelper.read_color_from_file(this, COLOR_FILE_NAME));
        syllabus_view.setAdapter(mAdapter);

        // 显示周末的信息
        weekend_text = (TextView) findViewById(R.id.weekend_syllabus_text);
        String text = "";
        if (MainActivity.weekends_syllabus_data.size() != 0){
            for(Lesson lesson : MainActivity.weekends_syllabus_data)
                text += lesson.weekend_classes() + "\n";
        }else{
            weekend_text.setVisibility(View.GONE);
        }
        weekend_text.setText(text);

        // 读取之前的壁纸
        load_syllabus_wallpaper();

        // 设置字体颜色
//        set_text_color(ColorHelper.read_color_from_file(this, COLOR_FILE_NAME));

        show_oa_button = (Button) findViewById(R.id.syllabus_show_oa_button);
        show_oa_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SyllabusActivity.this, OAActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_syllabus);
        setupViews();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setTitle(MainActivity.cur_year_string.replace("-", " ") + " " +
                    StringDataHelper.SEMESTER_LANGUAGE.get(StringDataHelper.semester_to_string(MainActivity.cur_semester)));
//        Toast.makeText(SyllabusActivity.this, "the token is " + MainActivity.token, Toast.LENGTH_SHORT).show();
    }

    public void showClassInfo(Lesson lesson){
        clicked_lesson = lesson;
        Intent tab_intent = new Intent(this, MyTabActivity.class);
        startActivity(tab_intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_syllabus, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.set_default_syllabus: {
                if (set_default_syllabus()) {
                    Toast.makeText(SyllabusActivity.this, "成功设置默认课表", Toast.LENGTH_SHORT).show();
                    return true;
                } else
                    return false;
            }
            case R.id.pick_wallpaper:
                pick_photo();
                break;

            case R.id.random_color_text:
                set_random_color();
                break;

//            case R.id.blue_text:
            case R.id.black_text:
//            case R.id.gray_text:
            case R.id.white_text:
                set_text_color(ColorHelper.get_color_from_id(item.getItemId()));
                break;

            case R.id.query_grade_action:
                // 查看成绩
                Intent grade_intent = new Intent(this, GradeActivity.class);
                startActivity(grade_intent);
                break;

            case R.id.global_discuss_action:
                Intent global_discuss_intent = new Intent(this, GlobalDiscussActivity.class);
                startActivity(global_discuss_intent);
                break;

            case R.id.query_exam_action:
                Intent exam_intent = new Intent(this, ExamActivity.class);
                startActivity(exam_intent);
                break;

            case R.id.sync_syllabus_action:
                // 更新课表
                sync_syllabus();
                break;
            case R.id.show_oa_action:
                Intent intent = new Intent(this, OAActivity.class);
                startActivity(intent);
                break;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);

    }

    /**
     * 设置默认学期
     */
    private boolean set_default_syllabus(){
        String syllabus_file_name = StringDataHelper.generate_syllabus_file_name(MainActivity.cur_username, MainActivity.cur_year_string,
                MainActivity.cur_semester, "_");
        // Debug
//        Toast.makeText(SyllabusActivity.this, syllabus_view.getWidth() + ", " + syllabus_view.getHeight(), Toast.LENGTH_SHORT).show();
        return FileOperation.save_to_file(this, DEFAULT_SYLLABUS_FILE, syllabus_file_name);

    }

    private void pick_photo(){
        Intent pick_intent = new Intent(Intent.ACTION_PICK, null);
        if (!FileOperation.is_sd_mounted() || !FileOperation.create_app_folder()) {
            Toast.makeText(SyllabusActivity.this, "读取错误", Toast.LENGTH_SHORT).show();
            return;
        }
        pick_intent.setDataAndType(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(pick_intent, PICK_PHOTO_FROM_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch (requestCode){
            case PICK_PHOTO_FROM_GALLERY:
                if (resultCode == RESULT_OK) {
                    int width = syllabus_view.getWidth();
                    int height = syllabus_view.getHeight();
                    startPhotoZoom(data.getData(), width, height);
                }
                break;
            case CROP_PHOTO_REQUEST:
                if (resultCode == RESULT_OK)
                    set_syllabus_wallpaper();
                break;
        }
    }

    private void load_bitmap(String file_path){
        wall_paper = BitmapFactory.decodeFile(file_path);
        Drawable drawable = new BitmapDrawable(getResources(), wall_paper);
        syllabus_view.setBackground(drawable);
    }

    private void set_syllabus_wallpaper(){
        String file_path = Environment.getExternalStorageDirectory() + "/" + FileOperation.APP_FOLDER + "/" + WALL_PAPER_FILE_TEMP;
        String wall_paper_file = Environment.getExternalStorageDirectory() + "/" + FileOperation.APP_FOLDER + "/" + WALL_PAPER_FILE_NAME;
        FileOperation.copy_file(new File(file_path), new File(wall_paper_file));
        load_bitmap(wall_paper_file);
    }


    private void load_syllabus_wallpaper(){
        String file_path = FileOperation.get_app_folder(true) +  WALL_PAPER_FILE_NAME;
        File wall_paper_file = new File(file_path);
        if (wall_paper_file.exists()){
            load_bitmap(file_path);
        }
    }


    private void startPhotoZoom(Uri uri, int width, int height) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        // crop为true是设置在开启的intent中设置显示的view可以剪裁
        intent.putExtra("crop", "true");

        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", width);
        intent.putExtra("aspectY", height);

        // outputX,outputY 是剪裁图片的宽高
        intent.putExtra("outputX", width);
        intent.putExtra("outputY", height);
        intent.putExtra("return-data", false);

        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);

        Uri picture_uri = FileOperation.getTempUri();
        if (picture_uri == null)
            return;

        intent.putExtra(MediaStore.EXTRA_OUTPUT, picture_uri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());

        startActivityForResult(intent, CROP_PHOTO_REQUEST);
    }


    private void set_text_color(int color){

        mAdapter.set_text_color(color);
        mAdapter.set_color(color);
        ColorHelper.save_color_to_file(this, color, COLOR_FILE_NAME);
    }

    private void set_random_color(){
        int color = ColorHelper.get_random_color();
        set_text_color(color);
    }

    private void sync_syllabus(){
        LessonPullTask sync_task = new LessonPullTask(WebApi.get_server_address() + getString(R.string.syllabus_get_api), this);
        HashMap<String, String> postData = new HashMap<>();
        postData.put("username", MainActivity.cur_username);
        postData.put("password", MainActivity.cur_password);
        postData.put("submit", "query");
        postData.put("years", MainActivity.cur_year_string);
        postData.put("semester", MainActivity.cur_semester + "");
        sync_task.execute(postData);
    }

    @Override
    public void deal_with_lessons(String raw_data) {
        if (raw_data.isEmpty()){
            Toast.makeText(SyllabusActivity.this, "网络连接错误", Toast.LENGTH_SHORT).show();
            return;
        }

        ClassParser classParser = new ClassParser(this, this);
        if (classParser.parseJSON(raw_data, true)) {
            classParser.inflateTable();     // 用数据填充课表
            MainActivity.weekdays_syllabus_data = classParser.weekdays_syllabus_data;
            MainActivity.weekends_syllabus_data = classParser.weekend_classes;
//                    Log.d(TAG, "established adapter");

            // 保存文件 命名格式: name_years_semester
            String username = MainActivity.cur_username;
//                    String filename = username + "_" + YEARS[position] + "_"
//                            + semester;
            // 保存文件 格式是: 14xfdeng_2014-2015_autumn
            String filename = StringDataHelper.generate_syllabus_file_name(username, MainActivity.cur_year_string, MainActivity.cur_semester, "_");
            if (FileOperation.save_to_file(this, filename, raw_data)) {
//                        Toast.makeText(MainActivity.this, "成功保存文件 " + filename, Toast.LENGTH_SHORT).show();
                Log.d(MainActivity.TAG, "saved file " + filename);
            }
            // 保存用户文件
            FileOperation.save_user(this, MainActivity.USERNAME_FILE, MainActivity.PASSWORD_FILE, username, MainActivity.cur_password);
            SyllabusAdapter new_adapter = new SyllabusAdapter(MainActivity.weekdays_syllabus_data, this);
            syllabus_view.setAdapter(new_adapter);
            Toast.makeText(SyllabusActivity.this, "课表同步成功", Toast.LENGTH_SHORT).show();
        }
//                    Toast.makeText(MainActivity.this, "读取课表成功哟~~~~", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void get_token(String token) {
        // 保存token
        FileOperation.save_to_file(this, StringDataHelper.generate_token_file_name(MainActivity.cur_username), token);
        MainActivity.token = token;
    }
}
