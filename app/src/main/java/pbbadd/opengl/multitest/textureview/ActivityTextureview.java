package pbbadd.opengl.multitest.textureview;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import pbbadd.opengl.multitest.R;

public class ActivityTextureview extends AppCompatActivity {
    private static final String log_tag = "textureview";
    private Button control;
    private TextInputEditText tw;
    private TextInputEditText th;
    private TextInputEditText tcnt;
    private TextInputEditText tcol;
    private TextInputEditText tthreads_cnt;
    private TextInputEditText t_sync_interval_millis;
    private int par_w=512;
    private int par_h=512;
    private int par_cnt =64;
    private int par_col =64;
    private int par_threads_cnt =1;
    private int par_sync_interval_millis;
    private boolean is_started=false;

    private int textureview_container_every_size;
    private List<TextureviewRenderThread> render_thread_list;
    private List<TextureView> texture_view_list;
    private GridLayout textureview_container;
    private int textureview_released_cnt=0;

    private CheckBox checkbox_swap_buffer=null;
    private CheckBox checkbox_sync_every_rending=null;
    private CheckBox checkbox_sync_dup_every_rending=null;
    private CheckBox checkbox_sync=null;
    private CheckBox checkbox_sync_dup=null;

    private boolean should_swap_buffer=false;
    private boolean should_sync=false;
    private boolean should_sync_dup=false;
    private boolean should_sync_every_rending=false;
    private boolean should_sync_dup_every_rending=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_textureview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        init_component();
        control_setting();
        Log.d(log_tag,"activity onCreate()");

        control_setting_handler=new Handler(Looper.getMainLooper());
//        control_setting_auto_thread_create();
    }

    private void init_component() {
        control=findViewById(R.id.textureview_control);
        textureview_container = findViewById(R.id.textureview_container);
        tw=findViewById(R.id.textureview_w);
        th=findViewById(R.id.textureview_h);
        tcnt=findViewById(R.id.textureview_cnt);
        tcol=findViewById(R.id.textureview_column);
        tthreads_cnt=findViewById(R.id.textureview_threads_cnt);

        t_sync_interval_millis=findViewById(R.id.t_sync_interval_millis);

        checkbox_swap_buffer=findViewById(R.id.checkbox_swap_buffer);
        checkbox_sync_every_rending=findViewById(R.id.checkbox_sync_every_rending);
        checkbox_sync_dup_every_rending=findViewById(R.id.checkbox_sync_dup_every_rending);
        checkbox_sync=findViewById(R.id.checkbox_sync);
        checkbox_sync_dup=findViewById(R.id.checkbox_sync_dup);

    }
    private void set_params() {
        par_w =512;
        par_h =512;
        par_cnt =64;
        par_col =64;
        par_sync_interval_millis=16;
        par_w =Integer.parseInt(Objects.requireNonNull(tw.getText()).toString());
        par_h =Integer.parseInt(Objects.requireNonNull(th.getText()).toString());
        par_cnt =Integer.parseInt(Objects.requireNonNull(tcnt.getText()).toString());
        par_col =Integer.parseInt(Objects.requireNonNull(tcol.getText()).toString());
        textureview_container.setColumnCount(par_col);
        par_threads_cnt =Integer.parseInt(Objects.requireNonNull(tthreads_cnt.getText()).toString());
        par_sync_interval_millis=Integer.parseInt(Objects.requireNonNull(t_sync_interval_millis.getText()).toString());
        Log.d(log_tag,String.format(Locale.US,"w=%d,h=%d,cnt=%d,col=%d,thread cnt=%d", par_w, par_h, par_cnt, par_col,par_threads_cnt));

        should_swap_buffer=checkbox_swap_buffer.isChecked();
        should_sync_every_rending=checkbox_sync_every_rending.isChecked();
        should_sync_dup_every_rending=checkbox_sync_dup_every_rending.isChecked();
        should_sync=checkbox_sync.isChecked();
        should_sync_dup=checkbox_sync_dup.isChecked();
    }

    private void control_setting_start() {
        String s="■";
        control.setClickable(false);
        set_params();
        create_list();
        is_started=true;
        control.setText(s);
        control.setClickable(true);
    }

    private void control_setting_stop() {
        String d="▶";
        control.setClickable(false);
        destroy_list();
        is_started=false;
        control.setText(d);
        control.setClickable(true);
    }
    private void control_setting() {
        String d="▶";
        control.setText(d);
        is_started=false;
        control.setOnClickListener(v->{
            if(!is_started) { //false
                control_setting_start();
//                control_setting_auto_thread_create();
            } else {
//                control_setting_auto_thread_destroy();
                control_setting_stop();
            }
        });
    }

    private Thread control_setting_auto_thread=null;
    private boolean control_setting_auto_thread_is_running;
    private Handler control_setting_handler=null;
    private void control_setting_auto() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

//        control_setting_stop();
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        control_setting_start();

        control_setting_handler.post(new Runnable() {
            @Override
            public void run() {
                control.performClick();
            }
        });
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    private void control_setting_auto_thread_create() {
        control_setting_auto_thread=new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            while(control_setting_auto_thread_is_running) {
                control_setting_auto();
            }
        });
        control_setting_auto_thread_is_running=true;
        control_setting_auto_thread.start();
    }
    private void control_setting_auto_thread_destroy() {
        control_setting_auto_thread_is_running=false;
        control_setting_auto_thread.interrupt();
        try {
            control_setting_auto_thread.join(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        control_setting_auto_thread=null;
    }

    private void create_render_thread_list() {
        render_thread_list = new ArrayList<>();
        int i;
        for(i=0;i<par_threads_cnt;++i) {
            TextureviewRenderThread tv_rt = new TextureviewRenderThread(par_w,par_h,i);
            tv_rt.set_context(this);
            tv_rt.set_should_swap_buffer(should_swap_buffer);
            tv_rt.set_should_sync_every_rending(should_sync_every_rending);
            tv_rt.set_should_sync_dup_every_rending(should_sync_dup_every_rending);
            tv_rt.set_should_sync(should_sync);
            tv_rt.set_should_sync_dup(should_sync_dup);
            tv_rt.set_sync_interval_millis(par_sync_interval_millis);
            render_thread_list.add(tv_rt);
            tv_rt.start();
        }
    }

    private void destroy_render_thread_list() {
        for(TextureviewRenderThread tv_rt: render_thread_list) {
            tv_rt.stopRender();
        }
        render_thread_list.clear();
        // need remove list? how to?
    }
    private void create_texture_view_list() {
        texture_view_list=new ArrayList<>();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        textureview_container_every_size=(screenWidth- par_col *2-2)/ par_col;
        if(textureview_container_every_size<=0) textureview_container_every_size=1;

        int i,j,mod;
        mod=(par_cnt/par_threads_cnt);
        for(i=0,j=1;i<par_threads_cnt;++i) {
            TextureviewRenderThread tv_rt=render_thread_list.get(i);
            for (;j <= par_cnt; ++j) {
                TextureView tv = new TextureView(this);
                tv.setId(ViewGroup.generateViewId());
                tv.setLayerType(TextureView.LAYER_TYPE_HARDWARE, null);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(1,1,1,1);
                params.width=textureview_container_every_size;
                params.height=textureview_container_every_size;
                tv.setLayoutParams(params);
                textureview_container.addView(tv);
                texture_view_list.add(tv);
                SurfaceTextureListenerEnhanced sft_le = new SurfaceTextureListenerEnhanced(tv_rt);
                tv.setSurfaceTextureListener(sft_le);
                Log.d(log_tag, "create texture view and add to layout, tv"+j+" in thread"+i);
                if((j%mod)==0) {
                    if((i+1)==par_threads_cnt) {
                        Log.d(log_tag,"left texture view will all in this thread,"+i);
                    } else {
                        Log.d(log_tag,"change to new thread");
                        ++j;
                        break;
                    }
                }
            }
        }
    }

    private void destroy_texture_view_list() {
        for(TextureView tv:texture_view_list) {
            SurfaceTexture st=tv.getSurfaceTexture();
            if(st!=null) {
                st.release();
            }
            tv.setSurfaceTextureListener(null);
            textureview_container.removeView(tv);
        }
    }

    private void create_list() {
        create_render_thread_list();
        create_texture_view_list();
    }

    private void destroy_list() {
        destroy_texture_view_list();
        destroy_render_thread_list();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(log_tag,"activity onResume(), when i have been called?");
    }

    @Override
    protected void onPause() {
        super.onPause();
//        destroy_list();
        Log.d(log_tag, "activity onPause()");
    }

    @Override
    protected void onDestroy() {
        destroy_list();
        super.onDestroy();
        Log.d(log_tag, "activity destroy, onDestroy()");
    }


}

