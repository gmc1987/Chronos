<template>
  <div class="overview">
    <div class="header">
      <div>
        <div class="title">管理概览</div>
        <div class="subtitle">统一维护医院组织、人员、访问权限与门户资源</div>
      </div>
      <RouterLink class="primary" to="/portal">进入统一门户</RouterLink>
    </div>

    <div class="cards">
      <div class="card">
        <div class="label">组织与科室</div>
        <div class="value">基础数据</div>
        <div class="trend up">统一组织主数据</div>
      </div>
      <div class="card">
        <div class="label">员工与任职</div>
        <div class="value">人员目录</div>
        <div class="trend up">支持多组织任职</div>
      </div>
      <div class="card">
        <div class="label">角色与权限</div>
        <div class="value">访问控制</div>
        <div class="trend up">功能及数据双重授权</div>
      </div>
    </div>

    <div class="panels">
      <div class="panel">
        <div class="panel-title">IAM 核心能力</div>
        <div class="panel-body">
          <div>账号、组织、员工、角色、权限和数据范围已统一进入 IAM 管理。</div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-title">快捷入口</div>
        <div class="panel-body">
          <RouterLink to="/admin/organizations">维护医院机构</RouterLink>
          <RouterLink to="/admin/directory">维护员工与任职</RouterLink>
          <RouterLink to="/admin/roles">配置角色权限</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>
