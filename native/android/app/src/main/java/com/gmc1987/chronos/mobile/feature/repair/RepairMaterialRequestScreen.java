package com.gmc1987.chronos.mobile.feature.repair;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Material request form backed by current available inventory. */
public final class RepairMaterialRequestScreen {
    private final Activity activity;private final UiKit ui;private final AsyncRunner async;private final ScreenHost host;private final HttpClient http;private final String orderId;private final Runnable back;
    public RepairMaterialRequestScreen(Activity activity,UiKit ui,AsyncRunner async,ScreenHost host,HttpClient http,String orderId,Runnable back){this.activity=activity;this.ui=ui;this.async=async;this.host=host;this.http=http;this.orderId=orderId;this.back=back;}
    public void show(){host.show(ui.centered("正在加载物料…",true));async.run(()->http.get("/portal/facility/materials?page=0&size=100"),this::render,e->{LinearLayout page=ui.page("申请维修物料");page.addView(ui.error(ErrorMessages.display(e,"物料加载失败"),this::show));host.show(ui.scroll(page));});}
    private void render(JSONObject envelope){LinearLayout page=ui.page("申请维修物料");Button nav=ui.navigationButton("‹ 返回");nav.setOnClickListener(v->back.run());page.addView(nav);JSONArray content=envelope.optJSONObject("data").optJSONArray("content");List<JSONObject> values=new ArrayList<>();List<String> labels=new ArrayList<>();for(int i=0;content!=null&&i<content.length();i++){JSONObject item=content.optJSONObject(i);if(item!=null&&"ACTIVE".equals(item.optString("status"))&&item.optDouble("availableQuantity")>0){values.add(item);labels.add(item.optString("materialName")+"（可用 "+item.optString("availableQuantity")+item.optString("unit")+"）");}}if(values.isEmpty()){page.addView(ui.empty("暂无可领用物料"));host.show(ui.scroll(page));return;}Spinner material=new Spinner(activity);material.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels));EditText quantity=ui.input("申请数量",false),remark=ui.multilineInput("申请说明",3);page.addView(material);page.addView(quantity);page.addView(remark);Button submit=ui.primaryButton("提交物料申请");submit.setOnClickListener(v->{double count;try{count=Double.parseDouble(quantity.getText().toString());}catch(Exception e){count=0;}if(count<=0){android.widget.Toast.makeText(activity,"请输入有效申请数量",android.widget.Toast.LENGTH_LONG).show();return;}JSONObject selected=values.get(material.getSelectedItemPosition());JSONObject body=requestBody(selected,count,remark.getText().toString());submit.setEnabled(false);async.run(()->http.post("/portal/facility/material-requests",body,true),ignored->{android.widget.Toast.makeText(activity,"物料申请已提交",android.widget.Toast.LENGTH_SHORT).show();back.run();},e->{submit.setEnabled(true);android.widget.Toast.makeText(activity,ErrorMessages.display(e,"申请失败"),android.widget.Toast.LENGTH_LONG).show();});});page.addView(submit);host.show(ui.scroll(page));}

    private JSONObject requestBody(JSONObject selected,double quantity,String remark){
        try{
            JSONArray items=new JSONArray().put(new JSONObject()
                    .put("materialId",selected.optString("id"))
                    .put("quantity",quantity));
            return new JSONObject()
                    .put("repairOrderId",orderId)
                    .put("remark",remark)
                    .put("items",items);
        }catch(Exception ignored){
            return new JSONObject();
        }
    }
}
