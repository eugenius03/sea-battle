package com.chnu.seabattle.service;


import java.util.Optional;

public interface BaseService<T, I> {

    Optional<T> findById(I id);

    T create(T entity);

    T update(T entity);

    void deleteById(I id);
}


