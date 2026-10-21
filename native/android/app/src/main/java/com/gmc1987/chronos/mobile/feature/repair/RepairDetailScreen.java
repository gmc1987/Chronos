package com.gmc1987.chronos.mobile.feature.repair;

import android.app.Activity;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import org.json.JSONObject;

/** Native repair detail and lifecycle actions. */
public final class RepairDetailScreen {
    private final Activity activity;private final UiKit ui;private final AsyncRunner async;private final ScreenHost host;private final HttpClient http;private final String id;private final boolean assigned;private final Runnable back;
    public RepairDetailScreen(Activity activity,UiKit ui,AsyncRunner async,ScreenHost host,HttpClient http,String id,boolean assigned,Runnable back){this.activity=activity;this.ui=ui;this.async=async;this.host=host;this.http=http;this.id=id;this.assigned=assigned;this.back=back;}
    public void show(){host.show(ui.centered("正在加载报修详情…",true));async.run(()->http.get("/portal/facility/repair-orders/"+id),r->render(r.optJSONObject("data")),e->{LinearLayout page=ui.page("报修详情");page.addView(ui.error(ErrorMessages.display(e,"详情加载失败"),this::show));host.show(ui.scroll(page));});}
    private void render(JSONObject order) {
        LinearLayout page=ui.page("报修详情");Button backButton=ui.navigationButton("‹ 返回");backButton.setOnClickListener(v->back.run());page.addView(backButton);
        LinearLayout card=ui.card();row(card,"报修单号",order.optString("orderNo"));row(card,"状态",RepairListScreen.status(order.optString("status")));row(card,"故障",order.optString("title"));row(card,"位置",order.optString("location"));row(card,"故障描述",order.optString("description"));row(card,"维修人员",order.optString("technicianName","等待派单"));row(card,"维修结果",order.optString("completionNote","-"));page.addView(card);
        String status=order.optString("status");
        if(assigned&&"DISPATCHED".equals(status))page.addView(action("接单","/portal/facility/assigned-repair-orders/"+id+"/accept",new JSONObject()));
        if(assigned&&("ACCEPTED".equals(status)||"WAITING_MATERIAL".equals(status)))page.addView(action("开始维修","/portal/facility/assigned-repair-orders/"+id+"/start",new JSONObject()));
        if(assigned&&(status.equals("ACCEPTED")||status.equals("IN_PROGRESS")||status.equals("WAITING_MATERIAL"))){Button material=ui.secondaryButton("申请维修物料");material.setOnClickListener(v->new RepairMaterialRequestScreen(activity,ui,async,host,http,id,this::show).show());page.addView(material);}
        if(assigned&&(status.equals("IN_PROGRESS")||status.equals("WAITING_MATERIAL"))){Button complete=ui.primaryButton("维修完成");complete.setOnClickListener(v->new RepairCompleteScreen(activity,ui,async,host,http,id,this::show).show());page.addView(complete);}
        if(!assigned&&"COMPLETED".equals(status))page.addView(action("确认维修并关闭","/portal/facility/repair-orders/"+id+"/close",closeBody()));
        host.show(ui.scroll(page));
    }
    private void row(LinearLayout card,String label,String value){card.addView(ui.label(label+"："+value,14,UiKit.TEXT,false));}
    private Button action(String title,String path,JSONObject body){Button button=ui.primaryButton(title);button.setOnClickListener(v->{button.setEnabled(false);async.run(()->http.post(path,body,true),ignored->{android.widget.Toast.makeText(activity,"操作成功",android.widget.Toast.LENGTH_SHORT).show();show();},e->{button.setEnabled(true);android.widget.Toast.makeText(activity,ErrorMessages.display(e,"操作失败"),android.widget.Toast.LENGTH_LONG).show();});});return button;}
    private JSONObject closeBody(){try{return new JSONObject().put("rating",5).put("comment","Android端确认维修完成");}catch(Exception ignored){return new JSONObject();}}
}
