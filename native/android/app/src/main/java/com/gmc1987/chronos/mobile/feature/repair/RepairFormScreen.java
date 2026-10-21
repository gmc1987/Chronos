package com.gmc1987.chronos.mobile.feature.repair;

import android.app.Activity;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import com.gmc1987.chronos.mobile.MainActivity;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** One-screen native repair form with mandatory photo upload. */
public final class RepairFormScreen {
    private final Activity activity;private final UiKit ui;private final AsyncRunner async;private final ScreenHost host;private final HttpClient http;private final Runnable back;
    private Uri photo;private Button photoButton;
    public RepairFormScreen(Activity activity,UiKit ui,AsyncRunner async,ScreenHost host,HttpClient http,Runnable back){this.activity=activity;this.ui=ui;this.async=async;this.host=host;this.http=http;this.back=back;}
    public void show(){LinearLayout page=ui.page("提交故障报修");Button backButton=ui.navigationButton("‹ 返回");backButton.setOnClickListener(v->back.run());page.addView(backButton);EditText title=ui.input("故障标题",false),location=ui.input("故障位置",false),description=ui.multilineInput("详细描述",4),contact=ui.input("联系人",false),phone=ui.input("联系电话",false);Spinner category=new Spinner(activity);String[] labels={"电气","给排水","网络","空调","家具","安防","其他"};String[] codes={"ELECTRICAL","WATER","NETWORK","HVAC","FURNITURE","SECURITY","OTHER"};category.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels));page.addView(title);page.addView(category);page.addView(location);page.addView(description);page.addView(contact);page.addView(phone);photoButton=ui.secondaryButton("选择故障照片");photoButton.setOnClickListener(v->((MainActivity)activity).documentPicker().openImage(activity,uri->{photo=uri;photoButton.setText("已选择照片");}));page.addView(photoButton);Button submit=ui.primaryButton("提交报修");submit.setOnClickListener(v->{if(title.getText().toString().trim().isEmpty()||location.getText().toString().trim().isEmpty()||description.getText().toString().trim().isEmpty()||photo==null){android.widget.Toast.makeText(activity,"请完整填写故障信息并选择照片",android.widget.Toast.LENGTH_LONG).show();return;}submit.setEnabled(false);async.run(()->{byte[] bytes=read(photo);JSONObject upload=http.upload("/files?businessType=WORKFLOW_FORM_DRAFT","repair.jpg","image/jpeg",bytes);String fileId=upload.getJSONObject("data").getString("id");JSONObject body=new JSONObject().put("title",title.getText().toString()).put("category",codes[category.getSelectedItemPosition()]).put("description",description.getText().toString()).put("location",location.getText().toString()).put("urgency","NORMAL").put("contactName",contact.getText().toString()).put("contactPhone",phone.getText().toString()).put("dispatchMode","AUTO").put("reportFileIds",new JSONArray().put(fileId));return http.post("/portal/facility/repair-orders",body,true);},ignored->{android.widget.Toast.makeText(activity,"报修提交成功",android.widget.Toast.LENGTH_SHORT).show();back.run();},e->{submit.setEnabled(true);android.widget.Toast.makeText(activity,ErrorMessages.display(e,"报修提交失败"),android.widget.Toast.LENGTH_LONG).show();});});page.addView(submit);host.show(ui.scroll(page));}
    private byte[] read(Uri uri)throws Exception{try(InputStream input=activity.getContentResolver().openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);return output.toByteArray();}}
}
