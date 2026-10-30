package com.quantify.app.data.repository

import com.quantify.app.data.local.dao.TagDao
import com.quantify.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao
) {
    fun getAll(): Flow<List<TagEntity>> = tagDao.getAll()
    suspend fun insert(tag: TagEntity) = tagDao.insert(tag)
    suspend fun delete(tag: TagEntity) = tagDao.delete(tag)
    suspend fun deleteAll() = tagDao.deleteAll()
}
