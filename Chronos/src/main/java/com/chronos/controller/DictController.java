package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.DictDTO;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
	public ResultData<List<DictVO>> tree() {
		return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.getTree()).build();
	}

	@GetMapping({ "/list" })
	public ResultData<List<DictVO>> listByCode(@RequestParam(required = false) String dictCode) {
		if (dictCode == null || dictCode.isEmpty()) {
			return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.getTree())
					.build();
		}
		return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.listByCode(dictCode))
				.build();
	}

	@GetMapping({ "/{id}" })
	public ResultData<DictVO> getById(@PathVariable("id") String id) {
		DictVO vo = this.dictService.getById(id);
		if (vo == null)
			return ResultData.<DictVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<DictVO>builder().code("200").msg("success").data(vo).build();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody DictDTO dto) {
		this.dictService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	public ResultData<Void> update(@Valid @RequestBody DictDTO dto) {
		this.dictService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	public ResultData<Void> delete(@PathVariable("id") String id) {
		this.dictService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}

