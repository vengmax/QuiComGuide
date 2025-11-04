package com.wllcom.quicomguide.data.repository

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.dao.CourseDao
import com.wllcom.quicomguide.data.local.dao.GroupDao
import com.wllcom.quicomguide.data.local.dao.MaterialDao
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.source.cloud.google.GoogleStorageDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val google: GoogleStorageDataSource,
    private val matRep: MaterialsRepository,
    private val settigsRepository: SettingsRepository,
) {

    private var localUniqueCourse: MutableList<String> = mutableListOf()
    private var remoteUniqueCourse: MutableList<String> = mutableListOf()

    private data class LocalGroup(val name: String, val courseName: String, val parentId: String? = null)
    private var localUniqueGroup: MutableList<LocalGroup> = mutableListOf()
    private data class RemoteGroup(val name: String, val courseName: String)
    private var remoteUniqueGroup: MutableList<RemoteGroup> = mutableListOf()

    private data class LocalMaterial(val name: String, val courseName: String, val groupName: String? = null, val parentId: String? = null)
    private var localUniqueMaterial: MutableList<LocalMaterial> = mutableListOf()
    private data class RemoteMaterial(val name: String, val courseName: String, val groupName: String? = null, val materialId: String)
    private var remoteUniqueMaterial: MutableList<RemoteMaterial> = mutableListOf()

    suspend fun getUserInfo(accessToken:String) = google.getUserInfo(accessToken)
    suspend fun uploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.uploadMaterial(accessToken, uniqueFileName, xml, uniqueCourseName, uniqueGroupName)
    suspend fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.downloadMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)
    suspend fun deleteMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.deleteMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)

    suspend fun createGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?) =
        google.createGroup(accessToken, uniqueGroupName, uniqueCourseName)
    suspend fun deleteGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?) =
        google.deleteGroup(accessToken, uniqueGroupName, uniqueCourseName)

    suspend fun createCourse(accessToken:String, uniqueCourseName: String) =
        google.createCourse(accessToken, uniqueCourseName)
    suspend fun deleteCourse(accessToken:String, uniqueCourseName: String) =
        google.deleteCourse(accessToken, uniqueCourseName)


    private val _syncPercentage = MutableStateFlow<Int?>(0)
    val syncPercentage: StateFlow<Int?> = _syncPercentage.asStateFlow()
    enum class StatusSync{ERROR, CONFLICT, SUCCESSFUL}
    enum class SyncMode{AUTO, SOFT_LOCAL, SOFT_REMOTE, FORCE_LOCAL, FORCE_REMOTE, MERGE}
    suspend fun sync(accessToken: String, mode: SyncMode = SyncMode.AUTO): StatusSync = withContext(Dispatchers.IO) {

        var returnType = StatusSync.SUCCESSFUL
        _syncPercentage.value = 0

        val db = AppDatabase.getInstance(appContext)
        val dao = db.materialDao()
        val groupDao = db.groupDao()
        val courseDao = db.courseDao()

        if(mode == SyncMode.AUTO) {
            localUniqueCourse = mutableListOf()
            remoteUniqueCourse = mutableListOf()

            localUniqueGroup = mutableListOf()
            remoteUniqueGroup = mutableListOf()

            localUniqueMaterial = mutableListOf()
            remoteUniqueMaterial = mutableListOf()

            val (statusListCourses, remoteMetaListCourses) = google.listCourses(accessToken)
            if (!statusListCourses)
                return@withContext StatusSync.ERROR
//            Log.d(TAG, "Courses: $remoteMetaListCourses")

            val localListCourses = courseDao.getAllCourses().map { it.name }
            val remoteListCourses = remoteMetaListCourses.map { it.second }
            localUniqueCourse = localListCourses.filterNot { it in remoteListCourses }.toMutableList()
            remoteUniqueCourse = remoteListCourses.filterNot { it in localListCourses }.toMutableList()
            remoteUniqueCourse.remove(google.generalCourseFolderName)

            // Только уникальные данные, которые находяться на телефоне
            localUniqueCourse.map { course ->
                val daoCourseId = courseDao.getCourseByName(course)!!.id
                val localListGroups = groupDao.getGroupsByCourseId(daoCourseId)
                localUniqueGroup.addAll(
                    localListGroups
                    .map { group -> group.name }
                    .map { groupName -> LocalGroup(groupName, course) })

                localListGroups.forEach { group ->
                    val localListMaterial = dao.getMaterialsByGroupId(group.id).map { mat -> mat.title }
                    localUniqueMaterial.addAll(
                        localListMaterial
                            .map { matTitle -> LocalMaterial(matTitle, course, group.name) })
                }

                val localListMaterials = dao.getNoGroupedMaterialsByCourseId(daoCourseId)
                localUniqueMaterial.addAll(localListMaterials.map { mat -> LocalMaterial(mat.title, course) })
            }




            remoteMetaListCourses.forEach { course ->
                // ======================================================================================================
                val (statusListGroups, remoteMetaListGroups) = google.listGroupsById(accessToken, course.first)
                if (!statusListGroups)
                    return@withContext StatusSync.ERROR
//                Log.d(TAG, "Course \"$course\", Groups: $remoteMetaListGroups")

                val remoteListGroups = remoteMetaListGroups.map { group -> group.second }
                // Добавление новых групп из нового курса на диске
                if (course.second in remoteUniqueCourse) {
                    remoteUniqueGroup.addAll(
                        remoteListGroups
                            .map { groupName -> RemoteGroup(groupName, course.second) })
                }
                // Добавление групп которых нет
                else if (course.second !in localUniqueCourse && course.second !in remoteUniqueCourse) {
                    val localListGroups = if (google.generalCourseFolderName == course.second) {
                        groupDao.getGroupsByCourseId(null)
                            .map { group -> group.name }
                    } else {
                        groupDao.getGroupsByCourseId(courseDao.getCourseByName(course.second)!!.id)
                            .map { group -> group.name }
                    }

                    localUniqueGroup.addAll(
                        localListGroups
                            .filterNot { groupName -> groupName in remoteListGroups }
                            .also {
                                it.map { groupName ->
                                    val localListMaterials =
                                        dao.getMaterialsByGroupId(groupDao.getGroupByName(groupName)!!.id)
                                            .map { mat -> mat.title }

                                    localUniqueMaterial.addAll(
                                        localListMaterials
                                            .map { matTitle -> LocalMaterial(matTitle, course.second, groupName) }
                                    )
                                }
                            }
                            .map { groupName -> LocalGroup(groupName, course.second, course.first) }
                    )
                    remoteUniqueGroup.addAll(
                        remoteListGroups
                            .filterNot { groupName -> groupName in localListGroups }
                            .map { groupName -> RemoteGroup(groupName, course.second) }
                    )
                }
                // ======================================================================================================

                // ======================================================================================================
                remoteMetaListGroups.forEach { group ->
                    val (statusListMaterials, remoteMetaListMaterials) = google.listMaterialsById(
                        accessToken,
                        group.first
                    )
                    if (!statusListMaterials)
                        return@withContext StatusSync.ERROR
//                    Log.d(TAG, "Course \"$course\", Group: \"$group\", Materials: $remoteMetaListMaterials")

                    val remoteListMaterials = remoteMetaListMaterials.map { mat -> mat.second }
                    // Добавление новых материалов из нового курса на диске
                    if (course.second in remoteUniqueCourse) {
                        remoteUniqueMaterial.addAll(
                            remoteMetaListMaterials
                                .map { (matId, matTitle) -> RemoteMaterial(matTitle, course.second, group.second, matId) })
                    }
                    // Добавление материалов которых нет или обновленны
                    else if (course.second !in localUniqueCourse && course.second !in remoteUniqueCourse) {
                        val localListGroups = if (google.generalCourseFolderName == course.second) {
                            groupDao.getGroupsByCourseId(null)
                                .map { group -> group.name }
                        } else {
                            groupDao.getGroupsByCourseId(courseDao.getCourseByName(course.second)!!.id)
                                .map { group -> group.name }
                        }

                        if (group.second !in localListGroups) {
                            remoteUniqueMaterial.addAll(
                                remoteMetaListMaterials
                                    .map { (matId, matTitle) -> RemoteMaterial(matTitle, course.second, group.second, matId) })
                        } else {
                            val localListMaterials =
                                dao.getMaterialsByGroupId(groupDao.getGroupByName(group.second)!!.id)

                            val localListMaterialsTitle = localListMaterials.map { mat -> mat.title }

                            localUniqueMaterial.addAll(
                                localListMaterialsTitle
                                    .filterNot { matTitle -> matTitle in remoteListMaterials }
                                    .map { matTitle ->
                                        LocalMaterial(
                                            matTitle,
                                            course.second,
                                            group.second,
                                            group.first
                                        )
                                    }
                            )
                            remoteUniqueMaterial.addAll(
                                remoteMetaListMaterials
                                    .filterNot { (matId, matTitle) -> matTitle in localListMaterialsTitle }
                                    .map { (matId, matTitle) ->
                                        RemoteMaterial(
                                            matTitle,
                                            course.second,
                                            group.second,
                                            matId
                                        )
                                    }
                            )

                            // Проверяем обновился ли материал
                            remoteMetaListMaterials.forEach { (id, name, modified) ->
                                localListMaterials.forEach { mat ->
                                    if(name == mat.title){
                                        val driveModifiedMillis = Instant.parse(modified).toEpochMilli()
                                        if(driveModifiedMillis > mat.updatedAt){
                                            remoteUniqueMaterial.add(RemoteMaterial(
                                                name,
                                                course.second,
                                                group.second,
                                                id
                                            ))
                                        }
                                        else if (mat.updatedAt > driveModifiedMillis){
                                            localUniqueMaterial.add(LocalMaterial(
                                                name,
                                                course.second,
                                                group.second,
                                                group.first
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // ======================================================================================================


                // ======================================================================================================
                val (statusListMaterials, remoteMetaListMaterials) = google.listMaterialsById(accessToken, course.first)
                if (!statusListMaterials)
                    return@withContext StatusSync.ERROR
//                Log.d(TAG, "Course \"$course\", Materials: $remoteMetaListMaterials")

                val remoteListMaterials = remoteMetaListMaterials.map { mat -> mat.second }
                // Добавление новых материалов из нового курса на диске
                if (course.second in remoteUniqueCourse) {
                    remoteUniqueMaterial.addAll(
                        remoteMetaListMaterials
                            .map { (matId, matTitle) -> RemoteMaterial(matTitle, course.second, materialId = matId) })
                }
                // Добавление материалов которых нет или обновленны
                else if (course.second !in localUniqueCourse && course.second !in remoteUniqueCourse) {
                    val localListMaterials = if (google.generalCourseFolderName == course.second) {
                        dao.getNoGroupedMaterialsByCourseId(null)
                    } else {
                        dao.getNoGroupedMaterialsByCourseId(courseDao.getCourseByName(course.second)!!.id)
                    }
                    val localListMaterialsTitle = localListMaterials.map { mat -> mat.title }

                    localUniqueMaterial.addAll(
                        localListMaterialsTitle
                            .filterNot { matTitle -> matTitle in remoteListMaterials }
                            .map { matTitle -> LocalMaterial(matTitle, course.second, null, course.first) }
                    )
                    remoteUniqueMaterial.addAll(
                        remoteMetaListMaterials
                            .filterNot { (matId, matTitle) -> matTitle in localListMaterialsTitle }
                            .map { (matId, matTitle) -> RemoteMaterial(matTitle, course.second, materialId = matId) }
                    )

                    // Проверяем обновился ли материал
                    remoteMetaListMaterials.forEach { (id, name, modified) ->
                        localListMaterials.forEach { mat ->
                            if(name == mat.title){
                                val driveModifiedMillis = Instant.parse(modified).toEpochMilli()
                                if(driveModifiedMillis > mat.updatedAt){
                                    remoteUniqueMaterial.add(RemoteMaterial(
                                        name,
                                        course.second,
                                        null,
                                        id
                                    ))
                                }
                                else if (mat.updatedAt > driveModifiedMillis){
                                    localUniqueMaterial.add(LocalMaterial(
                                        name,
                                        course.second,
                                        null,
                                        course.first
                                    ))
                                }
                            }
                        }
                    }
                }
                // ======================================================================================================
            }
            if(settigsRepository.accountFlow.first() != settigsRepository.lastAccountSyncFlow.first()){
                return@withContext StatusSync.CONFLICT
            }
//            Log.d(TAG, "Done!!!!!!!!!!!!!!!!!")
        }

//        Log.d(TAG, "SYNC: Courses: $localUniqueCourse")
//        Log.d(TAG, "SYNC: Groups: $localUniqueGroup")
//        Log.d(TAG, "SYNC: Materials: $localUniqueMaterial")
//
//        Log.d(TAG, "SYNC: Courses: $remoteUniqueCourse")
//        Log.d(TAG, "SYNC: Groups: $remoteUniqueGroup")
//        Log.d(TAG, "SYNC: Materials: $remoteUniqueMaterial")

        var currentMode = mode
        if(currentMode == SyncMode.AUTO){
            if(localUniqueCourse.isEmpty() && localUniqueGroup.isEmpty() && localUniqueMaterial.isEmpty() &&
                (remoteUniqueCourse.isNotEmpty() || remoteUniqueGroup.isNotEmpty() || remoteUniqueMaterial.isNotEmpty())){
                currentMode = SyncMode.SOFT_REMOTE
            }
            else if (remoteUniqueCourse.isEmpty() && remoteUniqueGroup.isEmpty() && remoteUniqueMaterial.isEmpty() &&
                (localUniqueCourse.isNotEmpty() || localUniqueGroup.isNotEmpty() || localUniqueMaterial.isNotEmpty())){
                currentMode = SyncMode.SOFT_LOCAL
            }
            else if (localUniqueCourse.isEmpty() && localUniqueGroup.isEmpty() && localUniqueMaterial.isEmpty() &&
                remoteUniqueCourse.isEmpty() && remoteUniqueGroup.isEmpty() && remoteUniqueMaterial.isEmpty())
            {
                return@withContext StatusSync.SUCCESSFUL
            }
            else{
                return@withContext StatusSync.CONFLICT
            }
        }

        _syncPercentage.value = 50

        // Только локальные данные выгружаем
        if(currentMode == SyncMode.SOFT_LOCAL){
            returnType = syncSoftLocal(accessToken, dao, groupDao, courseDao)
        }
        else if(currentMode == SyncMode.SOFT_REMOTE){
            returnType = syncSoftRemote(accessToken, dao, groupDao, courseDao)
        }
        else if(currentMode == SyncMode.MERGE){
            returnType = syncSoftRemote(accessToken, dao, groupDao, courseDao)
            if(returnType != StatusSync.ERROR)
                returnType = syncSoftLocal(accessToken, dao, groupDao, courseDao)
        }
        else if(currentMode == SyncMode.FORCE_LOCAL){
            val statusDeleteAll = google.deleteAll(accessToken)
            if(!statusDeleteAll)
                return@withContext StatusSync.ERROR

            returnType = syncSoftLocal(accessToken, dao, groupDao, courseDao)
        }
        else if(currentMode == SyncMode.FORCE_REMOTE){
            db.clearAllTables()

            returnType = syncSoftRemote(accessToken, dao, groupDao, courseDao)
        }

        Log.d(TAG, "Done!!!!!!!!!!!!!!!!!")

        return@withContext returnType
    }

    private suspend fun syncSoftLocal(
        accessToken: String,
        dao: MaterialDao,
        groupDao: GroupDao,
        courseDao: CourseDao
    ): StatusSync = withContext(Dispatchers.IO) {
        // Случай с новыми курсами
        localUniqueCourse.forEach { localCourse ->
            val newCourseId = google.createCourse(accessToken, localCourse)
            if (newCourseId == null)
                return@withContext StatusSync.ERROR

            val listGroupByThisCourse = localUniqueGroup.filter { it.courseName == localCourse }
            listGroupByThisCourse.forEach { localGroup ->
                val newGroupId = google.fastCreateGroup(accessToken, localGroup.name, newCourseId)
                if (newGroupId == null)
                    return@withContext StatusSync.ERROR

                val listMaterialByThisCourseGroup =
                    localUniqueMaterial.filter { it.courseName == localCourse && it.groupName == localGroup.name }
                listMaterialByThisCourseGroup.forEach { localMat ->
                    val xml = dao.getMaterialByTitle(localMat.name)!!.xmlRaw
                    val modifiedTimeMat = google.fastUploadMaterial(accessToken, localMat.name, xml, newGroupId)
                    if (modifiedTimeMat == null)
                        return@withContext StatusSync.ERROR
                    else{
                        val idMat = dao.getMaterialByTitle(localMat.name)!!.id
                        val driveModifiedMillis = Instant.parse(modifiedTimeMat).toEpochMilli()
                        dao.updateMaterialUpdatedAt(idMat, driveModifiedMillis)
                    }
                }
            }

            val listMaterialByThisCourse =
                localUniqueMaterial.filter { it.courseName == localCourse && it.groupName == null }
            listMaterialByThisCourse.forEach { localMat ->
                val xml = dao.getMaterialByTitle(localMat.name)!!.xmlRaw
                val modifiedTimeMat = google.fastUploadMaterial(accessToken, localMat.name, xml, newCourseId)
                if (modifiedTimeMat == null)
                    return@withContext StatusSync.ERROR
                else{
                    val idMat = dao.getMaterialByTitle(localMat.name)!!.id
                    val driveModifiedMillis = Instant.parse(modifiedTimeMat).toEpochMilli()
                    dao.updateMaterialUpdatedAt(idMat, driveModifiedMillis)
                }
            }
        }

        // id Основного курса для оптимизации
        val generalCourseId = google.createCourse(accessToken, google.generalCourseFolderName)

        // Случай с группами и с материалами в сущ. курсе курсе
        val listGroupByCourse = localUniqueGroup.filter {
            it.courseName == google.generalCourseFolderName || it.courseName !in localUniqueCourse
        }
        listGroupByCourse.forEach { localGroup ->
            val parentId = if (localGroup.courseName == google.generalCourseFolderName)
                generalCourseId!!
            else
                localGroup.parentId ?: google.createCourse(accessToken, localGroup.courseName)!!

            val newGroupId = google.fastCreateGroup(accessToken, localGroup.name, parentId)
            if (newGroupId == null)
                return@withContext StatusSync.ERROR

            val listMaterialByCourseGroup = localUniqueMaterial.filter {
                (it.courseName == google.generalCourseFolderName || it.courseName !in localUniqueCourse) && it.groupName == localGroup.name
            }
            listMaterialByCourseGroup.forEach { localMat ->
                val xml = dao.getMaterialByTitle(localMat.name)!!.xmlRaw
                val modifiedTimeMat = google.fastUploadMaterial(accessToken, localMat.name, xml, newGroupId)
                if (modifiedTimeMat == null)
                    return@withContext StatusSync.ERROR
                else{
                    val idMat = dao.getMaterialByTitle(localMat.name)!!.id
                    val driveModifiedMillis = Instant.parse(modifiedTimeMat).toEpochMilli()
                    dao.updateMaterialUpdatedAt(idMat, driveModifiedMillis)
                }
            }
        }

        // Случай с материалами в сущ. курсе курсе
        val listLocalUniqueGroupName = localUniqueGroup.map { group -> group.name }
        val listMaterialByCourse = localUniqueMaterial.filter {
            (it.courseName == google.generalCourseFolderName || it.courseName !in localUniqueCourse) &&
                    (it.groupName == null ||it.groupName !in listLocalUniqueGroupName )
        }
        listMaterialByCourse.forEach { localMat ->
            val xml = dao.getMaterialByTitle(localMat.name)!!.xmlRaw
            if(localMat.groupName == null) {
                val parentId = if (localMat.courseName == google.generalCourseFolderName)
                    generalCourseId!!
                else
                    localMat.parentId ?: google.createCourse(accessToken, localMat.courseName)!!

                val modifiedTimeMat = google.fastUploadMaterial(accessToken, localMat.name, xml, parentId)
                if (modifiedTimeMat == null)
                    return@withContext StatusSync.ERROR
                else{
                    val idMat = dao.getMaterialByTitle(localMat.name)!!.id
                    val driveModifiedMillis = Instant.parse(modifiedTimeMat).toEpochMilli()
                    dao.updateMaterialUpdatedAt(idMat, driveModifiedMillis)
                }
            }
            else{
                val courseId = if (localMat.courseName == google.generalCourseFolderName)
                    generalCourseId!!
                else
                    google.createCourse(accessToken, localMat.courseName)!!
                val parentId = localMat.parentId ?: google.fastCreateGroup(accessToken, localMat.courseName, courseId)!!

                val modifiedTimeMat = google.fastUploadMaterial(accessToken, localMat.name, xml, parentId)
                if (modifiedTimeMat == null)
                    return@withContext StatusSync.ERROR
                else{
                    val idMat = dao.getMaterialByTitle(localMat.name)!!.id
                    val driveModifiedMillis = Instant.parse(modifiedTimeMat).toEpochMilli()
                    dao.updateMaterialUpdatedAt(idMat, driveModifiedMillis)
                }
            }
        }
        return@withContext StatusSync.SUCCESSFUL
    }

    private suspend fun syncSoftRemote(
        accessToken: String,
        dao: MaterialDao,
        groupDao: GroupDao,
        courseDao: CourseDao
    ): StatusSync = withContext(Dispatchers.IO) {

        // Случай с новыми курсами
        remoteUniqueCourse.forEach { remoteCourse ->
            val newCourseId = courseDao.insertCourse(CourseEntity(name = remoteCourse))

            val listGroupByThisCourse = remoteUniqueGroup.filter { it.courseName == remoteCourse  }
            listGroupByThisCourse.forEach { remoteGroup ->
                val newGroupId = groupDao.insertGroup(MaterialGroupEntity(
                    name = remoteGroup.name,
                    courseId = newCourseId
                ))

                val listMaterialByThisCourse = remoteUniqueMaterial.filter { it.courseName == remoteCourse && it.groupName == remoteGroup.name }
                listMaterialByThisCourse.forEach { remoteMat ->
                    val (statusDownloadMaterial, xml, modifiedTime) = google.fastDownloadMaterial(accessToken, remoteMat.materialId)
                    if(!statusDownloadMaterial)
                        return@withContext StatusSync.ERROR

                    // Добавляем материал
                    val idNewMaterial = matRep.addMaterial(remoteMat.name, xml)
                    if(idNewMaterial == null)
                        return@withContext StatusSync.ERROR

                    // Обновляем дату последнего изменения
                    val driveModifiedMillis = Instant.parse(modifiedTime).toEpochMilli()
                    dao.updateMaterialUpdatedAt(idNewMaterial, driveModifiedMillis)

                    // Добавляем связь с курсом
                    val crossRefCourse = MaterialCourseCrossRef(idNewMaterial, newCourseId)
                    dao.insertMaterialCourseCrossRefs(listOf(crossRefCourse))

                    // Добавляем связь с группой
                    val crossRefGroup = MaterialGroupCrossRef(idNewMaterial, newGroupId)
                    dao.insertMaterialGroupCrossRefs(listOf(crossRefGroup))
                }
            }

            val listMaterialByThisCourse = remoteUniqueMaterial.filter { it.courseName == remoteCourse && it.groupName == null }
            listMaterialByThisCourse.forEach { remoteMat ->
                val (statusDownloadMaterial, xml, modifiedTime) = google.fastDownloadMaterial(accessToken, remoteMat.materialId)
                if(!statusDownloadMaterial)
                    return@withContext StatusSync.ERROR

                // Добавляем материал
                val idNewMaterial = matRep.addMaterial(remoteMat.name, xml)
                if(idNewMaterial == null)
                    return@withContext StatusSync.ERROR

                // Обновляем дату последнего изменения
                val driveModifiedMillis = Instant.parse(modifiedTime).toEpochMilli()
                dao.updateMaterialUpdatedAt(idNewMaterial, driveModifiedMillis)

                // Добавляем связь с курсом
                val crossRef = MaterialCourseCrossRef(idNewMaterial, newCourseId)
                dao.insertMaterialCourseCrossRefs(listOf(crossRef))
            }
        }

        // Случай с группами и с материалами в сущ. курсе курсе
        val listGroupByCourse = remoteUniqueGroup.filter {
            it.courseName == google.generalCourseFolderName || it.courseName !in remoteUniqueCourse
        }
        listGroupByCourse.forEach { remoteGroup ->
            val courseId = if (remoteGroup.courseName == google.generalCourseFolderName)
                null
            else
                groupDao.getGroupByName(remoteGroup.courseName)!!.id

            val newGroupId = groupDao.insertGroup(MaterialGroupEntity(
                name = remoteGroup.name,
                courseId = courseId
            ))

            val listMaterialByCourse = remoteUniqueMaterial.filter {
                (it.courseName == google.generalCourseFolderName || it.courseName !in remoteUniqueCourse ) && it.groupName == remoteGroup.name
            }
            listMaterialByCourse.forEach { remoteMat ->
                val (statusDownloadMaterial, xml, modifiedTime) = google.fastDownloadMaterial(accessToken, remoteMat.materialId)
                if(!statusDownloadMaterial)
                    return@withContext StatusSync.ERROR

                // Добавляем материал
                val idNewMaterial = matRep.addMaterial(remoteMat.name, xml)
                if(idNewMaterial == null)
                    return@withContext StatusSync.ERROR

                // Обновляем дату последнего изменения
                val driveModifiedMillis = Instant.parse(modifiedTime).toEpochMilli()
                dao.updateMaterialUpdatedAt(idNewMaterial, driveModifiedMillis)

                // Добавляем связь с курсом
                if(courseId != null) {
                    val crossRefCourse = MaterialCourseCrossRef(idNewMaterial, courseId)
                    dao.insertMaterialCourseCrossRefs(listOf(crossRefCourse))
                }

                // Добавляем связь с группой
                val crossRefGroup = MaterialGroupCrossRef(idNewMaterial, newGroupId)
                dao.insertMaterialGroupCrossRefs(listOf(crossRefGroup))
            }
        }

        // Случай с материалами в сущ. курсе курсе
        val listRemoteUniqueGroupName = remoteUniqueGroup.map { group -> group.name }
        val listMaterialByCourse = remoteUniqueMaterial.filter {
            (it.courseName == google.generalCourseFolderName || it.courseName !in remoteUniqueCourse ) &&
                    (it.groupName == null || it.groupName !in listRemoteUniqueGroupName)
        }
        listMaterialByCourse.forEach { remoteMat ->
            val (statusDownloadMaterial, xml, modifiedTime) = google.fastDownloadMaterial(accessToken, remoteMat.materialId)
            if(!statusDownloadMaterial)
                return@withContext StatusSync.ERROR

            // Добавляем материал
            val materialLocal = dao.getMaterialByTitle(remoteMat.name)
            var idMaterial: Long?
            if(materialLocal == null) {
                idMaterial = matRep.addMaterial(remoteMat.name, xml)
                if (idMaterial == null)
                    return@withContext StatusSync.ERROR

                // Добавляем связь с курсом
                if(remoteMat.courseName != google.generalCourseFolderName) {
                    val courseId = courseDao.getCourseByName(remoteMat.courseName)!!.id
                    val crossRef = MaterialCourseCrossRef(idMaterial, courseId)
                    dao.insertMaterialCourseCrossRefs(listOf(crossRef))
                }

                // Добавляем связь с группой
                if(remoteMat.groupName != null) {
                    val groupId = groupDao.getGroupByName(remoteMat.groupName)!!.id
                    val groupRef = MaterialGroupCrossRef(idMaterial, groupId)
                    dao.insertMaterialGroupCrossRefs(listOf(groupRef))
                }
            }
            else {
                idMaterial = matRep.updateMaterial(materialLocal.id, xml)
                if (idMaterial == null)
                    return@withContext StatusSync.ERROR
            }

            // Обновляем дату последнего изменения
            val driveModifiedMillis = Instant.parse(modifiedTime).toEpochMilli()
            dao.updateMaterialUpdatedAt(idMaterial, driveModifiedMillis)
        }

        return@withContext StatusSync.SUCCESSFUL
    }
}