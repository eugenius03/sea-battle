package com.chnu.seabattle.service;

import com.chnu.seabattle.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional
public abstract class AbstractBaseService<T, ID> implements BaseService<T, ID> {

    protected abstract JpaRepository<T, ID> getRepository();

    public T create(T entity) {
        beforeCreate(entity);
        T saved = getRepository().save(entity);
        afterCreate(saved);
        return saved;
    }

    public T update(T entity) {
        beforeUpdate(entity);
        T saved = getRepository().save(entity);
        afterUpdate(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        return getRepository().findById(id);
    }

    @Transactional(readOnly = true)
    public List<T> findAll() {
        return getRepository().findAll();
    }

    public void deleteById(ID id) {
        T entity = getRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found"));
        beforeDelete(entity);
        getRepository().delete(entity);
        afterDelete(entity);
    }

    /* ---- Hooks (override only if needed) ---- */

    protected void beforeCreate(T entity) {
    }

    protected void afterCreate(T entity) {
    }

    protected void beforeUpdate(T entity) {
    }

    protected void afterUpdate(T entity) {
    }

    protected void beforeDelete(T entity) {
    }

    protected void afterDelete(T entity) {
    }
}
