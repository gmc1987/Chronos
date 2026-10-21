package com.gmc1987.chronos.mobile.feature.repair;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native repair list for reporters and maintenance technicians. */
public final class RepairListScreen {
    private final Activity activity; private final UiKit ui; private final AsyncRunner async;
    private final ScreenHost host; private final HttpClient http; private final Runnable back;
    private boolean assigned; private LinearLayout body;

    public RepairListScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, Runnable back) {
        this.activity=activity;this.ui=ui;this.async=async;this.host=host;this.http=http;this.back=back;
    }

    public void show() {
        LinearLayout page=ui.page("故障报修");
        LinearLayout navigation=ui.horizontal();
        Button backButton=ui.navigationButton("‹ 返回");backButton.setOnClickListener(v->back.run());navigation.addView(backButton);
        Button create=ui.primaryButton("我要报修");create.setOnClickListener(v->new RepairFormScreen(activity,ui,async,host,http,this::show).show());navigation.addView(create);
        page.addView(navigation);
        LinearLayout tabs=ui.horizontal();Button mine=ui.secondaryButton("我的报修");Button work=ui.secondaryButton("维修工单");
        mine.setOnClickListener(v->{assigned=false;load();});work.setOnClickListener(v->{assigned=true;load();});tabs.addView(mine);tabs.addView(work);page.addView(tabs);
        body=ui.vertical();page.addView(body);host.show(ui.scroll(page));load();
    }

    private void load(){body.removeAllViews();body.addView(ui.centered("正在加载…",true));String path=assigned?"/portal/facility/assigned-repair-orders?page=0&size=50":"/portal/facility/repair-orders?page=0&size=50";async.run(()->http.get(path),this::render,e->{body.removeAllViews();body.addView(ui.error(ErrorMessages.display(e,"报修单加载失败"),this::load));});}
    private void render(JSONObject envelope){body.removeAllViews();JSONObject data=envelope.optJSONObject("data");JSONArray rows=data==null?null:data.optJSONArray("content");if(rows==null||rows.length()==0){body.addView(ui.empty("暂无报修记录"));return;}for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;LinearLayout card=ui.card();card.addView(ui.label(row.optString("title","故障报修"),17,UiKit.TEXT,true));card.addView(ui.label(row.optString("orderNo")+" · "+status(row.optString("status")),13,UiKit.BRAND,false));card.addView(ui.label("位置："+row.optString("location","-"),14,UiKit.MUTED,false));card.setOnClickListener(v->new RepairDetailScreen(activity,ui,async,host,http,row.optString("id"),assigned,this::show).show());body.addView(card);}}
    static String status(String value){switch(value){case"PENDING_DISPATCH":return"待派单";case"DISPATCHED":return"已派单";case"ACCEPTED":return"已接单";case"IN_PROGRESS":return"维修中";case"WAITING_MATERIAL":return"待物料";case"COMPLETED":return"已完工待确认";case"CLOSED":return"已关闭";case"CANCELLED":return"已取消";default:return value;}}
}
