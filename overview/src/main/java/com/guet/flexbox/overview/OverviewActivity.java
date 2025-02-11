package com.guet.flexbox.overview;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.litho.Column;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.LithoView;
import com.facebook.litho.widget.Text;
import com.facebook.litho.widget.VerticalScroll;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaEdge;
import com.guet.flexbox.DynamicBox;
import com.guet.flexbox.EventListener;
import com.guet.flexbox.EventType;
import com.guet.flexbox.NodeInfo;

import java.util.Map;

import ch.ielse.view.SwitchView;
import es.dmoral.toasty.Toasty;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OverviewActivity
        extends AppCompatActivity
        implements View.OnClickListener,
        EventListener,
        Runnable,
        NestedScrollView.OnScrollChangeListener,
        SwipeRefreshLayout.OnRefreshListener {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LithoView mLithoView;
    private SwitchView mIsLiveReload;
    private SwitchView mIsOpenConsole;
    private ListView mConsole;

    private Handler mMainThread = new Handler();
    private SimpleHandler mNetwork = new SimpleHandler("network");
    private MockService mMockService;
    private ArrayAdapter<String> mAdapter;
    private NodeInfo mLayout;
    private Map<String, Object> mData;
    private Runnable mReload = new Runnable() {
        @WorkerThread
        @Override
        public void run() {
            try {
                Response<Map<String, Object>> dataResponse = mMockService.data().execute();
                Response<NodeInfo> layout = mMockService.layout().execute();
                Map<String, Object> dataBody = dataResponse.body();
                NodeInfo layoutBody = layout.body();
                runOnUiThread(() -> {
                    if (layoutBody != null) {
                        mLayout = layoutBody;
                    }
                    if (dataBody != null) {
                        mData = dataBody;
                    }
                    apply();
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toasty.error(getApplicationContext(), "刷新失败").show();
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }
    };

    private void apply() {
        if (mLayout != null) {
            ComponentContext c = mLithoView.getComponentContext();
            mLithoView.setComponentAsync(
                    VerticalScroll.create(c)
                            .onScrollChangeListener(this)
                            .childComponent(Column.create(c)
                                    .widthPx(toPx(360))
                                    .alignItems(YogaAlign.CENTER)
                                    .child(DynamicBox.create(c)
                                            .bind(mData)
                                            .layout(mLayout)
                                            .marginPx(YogaEdge.TOP, dp2px(20))
                                            .eventListener(this))
                                    .child(Text.create(c)
                                            .widthPx(toPx(360))
                                            .heightPx(toPx(40))
                                            .backgroundColor(getResources()
                                                    .getColor(R.color.colorPrimary))
                                            .textAlignment(Layout.Alignment.ALIGN_CENTER)
                                            .text("这里是布局的下边界")
                                            .textColor(Color.WHITE)
                                            .textSizePx(toPx(25))
                                            .typeface(Typeface.defaultFromStyle(Typeface.BOLD)))
                            ).build()
            );
        }
    }

    private static int toPx(float value) {
        return (int) (Resources.getSystem().getDisplayMetrics().widthPixels / 360f * value);
    }

    public static int dp2px(float dpValue) {
        return (int) (0.5f + dpValue * Resources.getSystem().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mNetwork.removeCallbacks(this);
        mNetwork.post(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNetwork.removeCallbacks(this);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mLithoView = findViewById(R.id.host);
        mSwipeRefreshLayout = findViewById(R.id.pull);
        mIsLiveReload = findViewById(R.id.is_live_reload);
        mIsOpenConsole = findViewById(R.id.is_open_console);
        mConsole = findViewById(R.id.console);
        findViewById(R.id.transition).setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        getResources().getColor(R.color.background),
                        Color.TRANSPARENT
                }));
        mSwipeRefreshLayout.setOnRefreshListener(this);
        mIsOpenConsole.setOnClickListener(this);
        mLithoView.setComponentTree(
                ComponentTree.create(mLithoView.getComponentContext())
                        .layoutThreadHandler(new SimpleLithoHandler(this))
                        .build()
        );
        mAdapter = new ArrayAdapter<>(this, R.layout.console_item, R.id.text);
        mConsole.setAdapter(mAdapter);
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String url = bundle.getString("url");
            if (url != null) {
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(url);
                }
                mMockService = new Retrofit.Builder()
                        .baseUrl(url)
                        .client(new OkHttpClient())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(MockService.class);
                onRefresh();
                return;
            }
        }
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.is_open_console) {
            mConsole.setVisibility(mIsOpenConsole.isOpened() ? View.VISIBLE : View.GONE);
        }
    }


    @Override
    public void onEvent(
            @NonNull EventType type,
            @Nullable String action
    ) {
        mAdapter.add("event type=" + type.name() + " : event action=" + action);
    }

    @Override
    public void onScrollChange(
            NestedScrollView v,
            int scrollX,
            int scrollY,
            int oldScrollX,
            int oldScrollY
    ) {
        if (!mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setEnabled(scrollY <= 0);
        } else {
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    @Override
    public void onRefresh() {
        mNetwork.removeCallbacks(mReload);
        mNetwork.post(mReload);
    }

    @Override
    public void run() {
        if (mIsLiveReload.isOpened()) {
            onRefresh();
        }
        mMainThread.postDelayed(this, 1000);
    }
}
