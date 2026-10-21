package com.gmc1987.chronos.mobile.feature.repair;

import android.app.Activity;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.MainActivity;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;

/** Completion form requiring a repair result and an on-site photo. */
public final class RepairCompleteScreen {
    private final Activity activity;private final UiKit ui;private final AsyncRunner async;private final ScreenHost host;private final HttpClient http;private final String id;private final Runnable back;private Uri photo;
    public RepairCompleteScreen(Activity activity,UiKit ui,AsyncRunner async,ScreenHost host,HttpClient http,String id,Runnable back){this.activity=activity;this.ui=ui;this.async=async;this.host=host;this.http=http;this.id=id;this.back=back;}
    public void show(){LinearLayout page=ui.page("提交维修结果");Button nav=ui.navigationButton("‹ 返回");nav.setOnClickListener(v->back.run());page.addView(nav);EditText note=ui.multilineInput("填写维修结果",4);page.addView(note);Button choose=ui.secondaryButton("选择完工照片");choose.setOnClickListener(v->((MainActivity)activity).documentPicker().openImage(activity,uri->{photo=uri;choose.setText("已选择完工照片");}));page.addView(choose);Button submit=ui.primaryButton("确认完工");submit.setOnClickListener(v->{if(note.getText().toString().trim().isEmpty()||photo==null){android.widget.Toast.makeText(activity,"请填写维修结果并选择照片",android.widget.Toast.LENGTH_LONG).show();return;}submit.setEnabled(false);async.run(()->{http.upload("/files?businessType=FACILITY_REPAIR_COMPLETION&businessId="+id,"completion.jpg","image/jpeg",read(photo));return http.post("/portal/facility/assigned-repair-orders/"+id+"/complete",new JSONObject().put("note",note.getText().toString()),true);},ignored->{android.widget.Toast.makeText(activity,"已提交完工",android.widget.Toast.LENGTH_SHORT).show();back.run();},e->{submit.setEnabled(true);android.widget.Toast.makeText(activity,ErrorMessages.display(e,"完工提交失败"),android.widget.Toast.LENGTH_LONG).show();});});page.addView(submit);host.show(ui.scroll(page));}
    private byte[] read(Uri uri)throws Exception{try(InputStream input=activity.getContentResolver().openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);return output.toByteArray();}}
}
