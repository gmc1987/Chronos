<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-content">
      <AdminTopbar />
      <main class="admin-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import AdminSidebar from './AdminSidebar.vue'
import AdminTopbar from './AdminTopbar.vue'
</script>
