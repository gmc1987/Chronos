package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.ConsumerUserDTO;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.model.vo.ConsumerUserVO;
import com.chronos.service.iService.IConsumerUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@RequestMapping({ "/admin/customers" })
public class CustomerController {
	@Autowired
	private IConsumerUserService consumerUserService;

	@GetMapping({ "/list" })
	public ResultData<Page<ConsumerUser>> list(ConsumerUserDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Page<ConsumerUser> users = this.consumerUserService.pageUsers(dto, (Pageable) pageRequest);
		return ResultData.<Page<ConsumerUser>>builder().code("200").msg("success").data(users).build();
	}

	@GetMapping({ "/{id}" })
	public ResultData<ConsumerUserVO> getById(@PathVariable("id") String id) {
		ConsumerUserVO vo = this.consumerUserService.getById(id);
		if (vo == null)
			return ResultData.<ConsumerUserVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<ConsumerUserVO>builder().code("200").msg("success").data(vo).build();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody ConsumerUserDTO dto) {
		this.consumerUserService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	public ResultData<Void> update(@Valid @RequestBody ConsumerUserDTO dto) {
		this.consumerUserService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	public ResultData<Void> delete(@PathVariable("id") String id) {
		this.consumerUserService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}
