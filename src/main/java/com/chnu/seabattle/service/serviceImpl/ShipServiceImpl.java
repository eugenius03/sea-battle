package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.repository.ShipRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.ShipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShipServiceImpl extends AbstractBaseService<Ship, Long> implements ShipService {

    private final ShipRepository shipRepository;

    @Override
    protected ShipRepository getRepository() {
        return shipRepository;
    }
}
