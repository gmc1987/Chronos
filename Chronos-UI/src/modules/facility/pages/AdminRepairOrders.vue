<template>
  <section class="page">
    <header><div><h2>报修单管理</h2><p>统一受理、自动派单、人工兜底和维修闭环。</p></div></header>
    <el-form class="filters" inline>
      <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="单号、标题、位置或人员" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="filters.status" clearable style="width:160px"><el-option v-for="(label,value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="orderNo" label="报修单号" min-width="170" />
      <el-table-column prop="title" label="故障" min-width="180" />
      <el-table-column label="分类" width="95"><template #default="{row}">{{ categoryLabels[row.category] || row.category }}</template></el-table-column>
      <el-table-column label="紧急程度" width="100"><template #default="{row}"><el-tag :type="row.urgency==='CRITICAL'?'danger':row.urgency==='URGENT'?'warning':'info'">{{ urgencyLabels[row.urgency] }}</el-tag></template></el-table-column>
      <el-table-column prop="location" label="位置" min-width="170" />
      <el-table-column prop="reporterName" label="报修人" width="110" />
      <el-table-column prop="technicianName" label="维修人员" width="110" />
      <el-table-column label="状态" width="120"><template #default="{row}"><el-tag>{{ statusLabels[row.status] || row.status }}</el-tag></template></el-table-column>
      <AdaptiveActionColumn label="操作" :width="230"><template #default="{row}"><div class="actions"><el-button link @click="openDetail(row)">详情</el-button><el-button v-if="['PENDING_DISPATCH','DISPATCHED'].includes(row.status)" link type="primary" @click="openDispatch(row)">派单</el-button><el-button v-if="row.status==='PENDING_DISPATCH'" link type="success" @click="autoDispatch(row)">自动派单</el-button></div></template></AdaptiveActionColumn>
    </el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    <el-dialog v-model="dispatchDialog" title="人工派单" width="520px"><el-form label-width="100px"><el-form-item label="维修人员"><el-select v-model="dispatchForm.technicianId" style="width:100%"><el-option v-for="item in activeTechnicians" :key="item.id" :value="item.id" :label="`${item.technicianName}（${item.currentLoad}/${item.maxConcurrent}）`" /></el-select></el-form-item><el-form-item label="派单说明"><el-input v-model="dispatchForm.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="dispatchDialog=false">取消</el-button><el-button type="primary" @click="submitDispatch">确认派单</el-button></template></el-dialog>
    <el-drawer v-model="detailDialog" title="报修单详情" size="620px"><template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item label="报修单号">{{detail.orderNo}}</el-descriptions-item><el-descriptions-item label="状态">{{statusLabels[detail.status]}}</el-descriptions-item><el-descriptions-item label="故障分类">{{categoryLabels[detail.category]}}</el-descriptions-item><el-descriptions-item label="紧急程度">{{urgencyLabels[detail.urgency]}}</el-descriptions-item><el-descriptions-item label="故障位置" :span="2">{{detail.location}}</el-descriptions-item><el-descriptions-item label="故障描述" :span="2">{{detail.description}}</el-descriptions-item><el-descriptions-item label="报修人">{{detail.reporterName}}</el-descriptions-item><el-descriptions-item label="联系电话">{{detail.contactPhone||'-'}}</el-descriptions-item><el-descriptions-item label="维修人员">{{detail.technicianName||'-'}}</el-descriptions-item><el-descriptions-item label="接单截止">{{detail.dispatchDeadline?dateTime(detail.dispatchDeadline):'-'}}</el-descriptions-item><el-descriptions-item label="派单次数">{{detail.dispatchAttempts}}</el-descriptions-item><el-descriptions-item label="维修结果">{{detail.completionNote||'-'}}</el-descriptions-item></el-descriptions><h3>处理记录</h3><el-timeline><el-timeline-item v-for="event in detail.events" :key="event.createTime+event.eventType" :timestamp="dateTime(event.createTime)">{{event.remark || event.eventType}}</el-timeline-item></el-timeline></template></el-drawer>
  </section>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { autoDispatchRepairOrder, dispatchRepairOrder, repairOrderDetail, repairOrders, technicians } from '../api'
import { categoryLabels, dateTime, statusLabels, urgencyLabels } from '../labels'
const rows=ref([]), total=ref(0), page=ref(1), size=ref(20), loading=ref(false), dispatchDialog=ref(false), detailDialog=ref(false), detail=ref(null), selected=ref(null), technicianRows=ref([])
const filters=reactive({keyword:'',status:''}), dispatchForm=reactive({technicianId:'',remark:''})
const activeTechnicians=computed(()=>technicianRows.value.filter(x=>x.active&&x.workStatus!=='OFFLINE'&&x.currentLoad<x.maxConcurrent))
const load=async()=>{loading.value=true;try{const r=await repairOrders({...filters,page:page.value-1,size:size.value});rows.value=r.data.content;total.value=r.data.totalElements}finally{loading.value=false}}
const search=()=>{page.value=1;load()}; const openDispatch=row=>{selected.value=row;Object.assign(dispatchForm,{technicianId:'',remark:''});dispatchDialog.value=true}
const submitDispatch=async()=>{if(!dispatchForm.technicianId)return ElMessage.warning('请选择维修人员');await dispatchRepairOrder(selected.value.id,{...dispatchForm});ElMessage.success('派单成功');dispatchDialog.value=false;load()}
const autoDispatch=async row=>{await autoDispatchRepairOrder(row.id);ElMessage.success('已执行自动派单');load()}
const openDetail=async row=>{detail.value=(await repairOrderDetail(row.id)).data;detailDialog.value=true}
onMounted(async()=>{technicianRows.value=(await technicians()).data||[];await load()})
</script>
<style scoped>.page{padding:24px}header{margin-bottom:18px}h2{margin:0 0 6px}p{margin:0;color:#84909a}.filters{margin-bottom:10px}.el-pagination{margin-top:10px;justify-content:flex-end}.actions{display:flex;flex-wrap:nowrap;white-space:nowrap}</style>
