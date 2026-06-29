package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.ConsumerUserDTO;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.model.vo.ConsumerUserVO;
import com.chronos.service.iService.IConsumerUserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/consumer/users" })
public class ConsumerUserController {
	@Autowired
	private IConsumerUserService consumerUserService;

	@PostMapping({ "/register" })
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<ConsumerUserVO> register(@Valid @RequestBody ConsumerUserDTO dto) {
		ConsumerUserVO vo = this.consumerUserService.registerByAccount(dto);
		return ResultData.<ConsumerUserVO>builder().code("201").msg("created").data(vo).build();
	}

	@PostMapping({ "/register/sms" })
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public ResultData<Void> registerBySms(@RequestBody ConsumerUserDTO dto) {
		return ResultData.<Void>builder().code("501").msg("sms registration not implemented").data(null).build();
	}

	@PostMapping({ "/register/oauth" })
	@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
	public ResultData<Void> registerByOauth(@RequestBody Map<String, String> body) {
		return ResultData.<Void>builder().code("501").msg("oauth registration not implemented").data(null).build();
	}

	@GetMapping({ "/list" })
	public ResultData<Page<ConsumerUser>> list(ConsumerUserDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Page<ConsumerUser> users = this.consumerUserService.pageUsers(dto, (Pageable) pageRequest);
		return ResultData.<Page<ConsumerUser>>builder().code("200").msg("success").data(users).build();
	}
}
