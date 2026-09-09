package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.DictDTO;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping({ "/admin/dicts" })
public class DictController {
	@Autowired
	private IDictService dictService;

	@GetMapping({ "/tree" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:view','iam:dictionary:manage')")
	public ResultData<List<DictVO>> tree() {
		return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.getTree()).build();
	}

	@GetMapping({ "/list" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:view','iam:dictionary:manage')")
	public ResultData<List<DictVO>> listByCode(@RequestParam(required = false) String dictCode) {
		if (dictCode == null || dictCode.isEmpty()) {
			return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.getTree())
					.build();
		}
		return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.listByCode(dictCode))
				.build();
	}

	/**
	 * 业务表单读取字典选项不等同于维护字典，因此只要求用户已登录。
	 * 返回值仅包含启用项，避免各页面重复实现状态过滤。
	 */
	@GetMapping({ "/options" })
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<DictVO>> options(@RequestParam String dictCode) {
		List<DictVO> options = this.dictService.listByCode(dictCode).stream()
				.filter(item -> Integer.valueOf(1).equals(item.getStatus()))
				.toList();
		return ResultData.<List<DictVO>>builder()
				.code("200")
				.msg("success")
				.data(options)
				.build();
	}

	@GetMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:view','iam:dictionary:manage')")
	public ResultData<DictVO> getById(@PathVariable("id") String id) {
		DictVO vo = this.dictService.getById(id);
		if (vo == null)
			return ResultData.<DictVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<DictVO>builder().code("200").msg("success").data(vo).build();
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:create','iam:dictionary:manage')")
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody DictDTO dto) {
		this.dictService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:update','iam:dictionary:manage')")
	public ResultData<Void> update(@Valid @RequestBody DictDTO dto) {
		this.dictService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:dictionary:delete','iam:dictionary:manage')")
	public ResultData<Void> delete(@PathVariable("id") String id) {
		this.dictService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}
