package com.koolikanaku.domain

object TaskMapper {

    fun buildTasksFromAnalysis(
        roomId: String,
        roomType: RoomType,
        mergedCounts: Map<String, Int>,
        existingTasks: List<CleaningTask> = emptyList()
    ): List<CleaningTask> {
        val detectedTasks = mutableListOf<CleaningTask>()
        val detectedKeySet = mutableSetOf<String>()

        for ((rawKey, count) in mergedCounts) {
            if (count <= 0) continue
            val catalogKey = TaskCatalog.resolveCatalogKey(rawKey) ?: rawKey
            val item = TaskCatalog.getItem(catalogKey)
            if (item != null && !detectedKeySet.contains(catalogKey)) {
                detectedKeySet.add(catalogKey)
                detectedTasks.add(
                    CleaningTask(
                        id = "${roomId}_${catalogKey}",
                        catalogKey = catalogKey,
                        titleEn = item.titleEn,
                        titleMl = item.titleMl,
                        quantity = count,
                        minutesPerUnit = item.minutesPerUnit,
                        materialCostInr = item.materialCostInr,
                        isSelected = false,
                        source = TaskSource.DETECTED
                    )
                )
            }
        }

        // Keep existing MANUAL tasks unless a DETECTED task with the same key was found
        val keptManualTasks = existingTasks.filter { task ->
            task.source == TaskSource.MANUAL && !detectedKeySet.contains(task.catalogKey)
        }
        val keptManualKeySet = keptManualTasks.map { it.catalogKey }.toSet()

        // Create TEMPLATE tasks for the room type if not already detected or manual
        val templateTasks = mutableListOf<CleaningTask>()
        for (templateKey in TaskCatalog.templateKeysForRoom(roomType)) {
            if (!detectedKeySet.contains(templateKey) && !keptManualKeySet.contains(templateKey)) {
                val item = TaskCatalog.getItem(templateKey)
                if (item != null) {
                    templateTasks.add(
                        CleaningTask(
                            id = "${roomId}_${templateKey}",
                            catalogKey = templateKey,
                            titleEn = item.titleEn,
                            titleMl = item.titleMl,
                            quantity = 1,
                            minutesPerUnit = item.minutesPerUnit,
                            materialCostInr = item.materialCostInr,
                            isSelected = false,
                            source = TaskSource.TEMPLATE
                        )
                    )
                }
            }
        }

        return detectedTasks + keptManualTasks + templateTasks
    }

    fun updateRoomTypeTemplates(
        roomId: String,
        newRoomType: RoomType,
        currentTasks: List<CleaningTask>
    ): List<CleaningTask> {
        // Keep all DETECTED and MANUAL rows exactly as they are
        val keptTasks = currentTasks.filter {
            it.source == TaskSource.DETECTED || it.source == TaskSource.MANUAL
        }
        val existingKeys = keptTasks.map { it.catalogKey }.toSet()

        val newTemplateTasks = mutableListOf<CleaningTask>()
        for (templateKey in TaskCatalog.templateKeysForRoom(newRoomType)) {
            if (!existingKeys.contains(templateKey)) {
                val item = TaskCatalog.getItem(templateKey)
                if (item != null) {
                    newTemplateTasks.add(
                        CleaningTask(
                            id = "${roomId}_${templateKey}",
                            catalogKey = templateKey,
                            titleEn = item.titleEn,
                            titleMl = item.titleMl,
                            quantity = 1,
                            minutesPerUnit = item.minutesPerUnit,
                            materialCostInr = item.materialCostInr,
                            isSelected = false,
                            source = TaskSource.TEMPLATE
                        )
                    )
                }
            }
        }

        return keptTasks + newTemplateTasks
    }
}
