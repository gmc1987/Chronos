package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/dicts" })
public class DictPublicController {
	@Autowired
	private IDictService dictService;

	@GetMapping({ "/list" })
	public ResultData<List<DictVO>> listByCode(@RequestParam(required = false) String dictCode) {
		if (dictCode == null || dictCode.isEmpty()) {
			return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.getTree())
					.build();
		}
		return ResultData.<List<DictVO>>builder().code("200").msg("success").data(this.dictService.listByCode(dictCode))
				.build();
	}
}
