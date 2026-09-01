 package com.chronos.controller;

 import com.chronos.commons.model.ResultData;
 import com.chronos.model.dto.MenuDTO;
 import com.chronos.model.pojo.Menu;
 import com.chronos.model.vo.MenuVO;
 import com.chronos.service.iService.IMenuService;
 import jakarta.validation.Valid;
 import java.util.List;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.PageRequest;
 import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
 import org.springframework.web.bind.annotation.DeleteMapping;
 import org.springframework.web.bind.annotation.GetMapping;
 import org.springframework.web.bind.annotation.PathVariable;
 import org.springframework.web.bind.annotation.PostMapping;
 import org.springframework.web.bind.annotation.PutMapping;
 import org.springframework.web.bind.annotation.RequestBody;
 import org.springframework.web.bind.annotation.RequestMapping;
 import org.springframework.web.bind.annotation.RequestParam;
 import org.springframework.web.bind.annotation.ResponseStatus;
 import org.springframework.web.bind.annotation.RestController;
 
 
 
 
 
 
 @RestController
 @RequestMapping({"/admin/menus"})
 @PreAuthorize("@iamAuthorization.has(authentication, 'iam:menu:manage')")
 public class MenuController
 {
   @Autowired
   private IMenuService menuService;
   
   @GetMapping({"/list"})
   public ResultData<Page<Menu>> list(MenuDTO dto, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
     PageRequest pageRequest = PageRequest.of(page, size);
     Page<Menu> items = this.menuService.pageMenus(dto, (Pageable)pageRequest);
     return ResultData.<Page<Menu>>builder().code("200").msg("success").data(items).build();
   }
   
   @GetMapping({"/{id}"})
   public ResultData<MenuVO> getById(@PathVariable("id") String id) {
     MenuVO vo = this.menuService.getMenuById(id);
     if (vo == null) return ResultData.<MenuVO>builder().code("404").msg("not found").data(null).build(); 
     return ResultData.<MenuVO>builder().code("200").msg("success").data(vo).build();
   }
   
   @GetMapping({"/tree"})
   public ResultData<List<MenuVO>> tree() {
     List<MenuVO> tree = this.menuService.getMenuTree();
     return ResultData.<List<MenuVO>>builder().code("200").msg("success").data(tree).build();
   }
   
   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   public ResultData<Void> create(@Valid @RequestBody MenuDTO dto) {
     this.menuService.save(dto);
     return ResultData.<Void>builder().code("201").msg("created").data(null).build();
   }
   
   @PutMapping
   public ResultData<Void> update(@Valid @RequestBody MenuDTO dto) {
     this.menuService.update(dto);
     return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
   }
   
   @DeleteMapping({"/{id}"})
   public ResultData<Void> delete(@PathVariable("id") String id) {
     this.menuService.delete(id);
     return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
   }
 }
