package com.chnu.seabattle.service;


import java.util.Optional;

public interface BaseService<T, ID> {

    Optional<T> findById(ID id);

    T create(T entity);

    T update(T entity);

    void deleteById(ID id);
}


