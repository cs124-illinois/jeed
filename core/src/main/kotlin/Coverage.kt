package edu.illinois.cs.cs125.jeed.core

private fun adjustCoverageForJavaFeatures(coverage: LineCoverage, features: List<FeatureName>): LineCoverage {
    if (coverage == LineCoverage.COVERED || coverage == LineCoverage.EMPTY) {
        return coverage
    }
    check(coverage in listOf(LineCoverage.NOT_COVERED, LineCoverage.PARTLY_COVERED)) {
        "Invalid coverage value: $coverage"
    }
    return when {
        features.contains(FeatureName.CLASS) -> LineCoverage.IGNORED
        features.contains(FeatureName.ASSERT) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        else -> coverage
    }
}

private fun adjustCoverageForKotlinFeatures(coverage: LineCoverage, features: List<FeatureName>): LineCoverage {
    if (coverage == LineCoverage.COVERED || coverage == LineCoverage.EMPTY) {
        return coverage
    }
    check(coverage in listOf(LineCoverage.NOT_COVERED, LineCoverage.PARTLY_COVERED)) {
        "Invalid coverage value: $coverage"
    }
    return when {
        features.contains(FeatureName.ASSERT) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.FOR_LOOP_STEP) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.FOR_LOOP_RANGE) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.ELVIS_OPERATOR) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.HAS_COMPANION_OBJECT) && coverage == LineCoverage.NOT_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.SAFE_CALL_OPERATOR) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.LAST_WHEN_ENTRY) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        features.contains(FeatureName.COLLECTION_INDEXING) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
        else -> coverage
    }
}

private fun CoverageResult.adjustWithJavaFeatures(sourceFeatures: FeaturesResults): CoverageResult {
    val byFile = byFile.mapValues { (filename, coverageMap) ->
        val featureMap = sourceFeatures.lookup("", filename).features.featureList.toLineMap()
        coverageMap.mapValues inner@{ (line, coverage) ->
            val features = featureMap[line]?.map { it.feature } ?: return@inner coverage
            adjustCoverageForJavaFeatures(coverage, features)
        }
    }
    return CoverageResult(byFile, byClass)
}

private fun FileCoverage.adjustWithJavaFeatures(fileFeatures: UnitFeatures): FileCoverage {
    val featureMap = fileFeatures.features.featureList.toLineMap()
    return mapValues { (line, coverage) ->
        val features = featureMap[line]?.map { it.feature } ?: return@mapValues coverage
        adjustCoverageForJavaFeatures(coverage, features)
    }
}

private fun CoverageResult.adjustWithKotlinFeatures(sourceFeatures: FeaturesResults): CoverageResult {
    val byFile = byFile.mapValues { (filename, coverageMap) ->
        val featureMap = sourceFeatures.lookup("", filename).features.featureList.toLineMap()
        coverageMap.mapValues inner@{ (line, coverage) ->
            val features = featureMap[line]?.map { it.feature } ?: return@inner coverage
            adjustCoverageForKotlinFeatures(coverage, features)
        }
    }
    return CoverageResult(byFile, byClass)
}

private fun FileCoverage.adjustWithKotlinFeatures(fileFeatures: UnitFeatures): FileCoverage {
    val featureMap = fileFeatures.features.featureList.toLineMap()
    return mapValues { (line, coverage) ->
        val features = featureMap[line]?.map { it.feature } ?: return@mapValues coverage
        adjustCoverageForKotlinFeatures(coverage, features)
    }
}

fun CoverageResult.adjustWithFeatures(sourceFeatures: FeaturesResults, language: Source.FileType) = when (language) {
    Source.FileType.JAVA -> adjustWithJavaFeatures(sourceFeatures)
    Source.FileType.KOTLIN -> adjustWithKotlinFeatures(sourceFeatures)
}

fun FileCoverage.adjustWithFeatures(fileFeatures: UnitFeatures, language: Source.FileType) = when (language) {
    Source.FileType.JAVA -> adjustWithJavaFeatures(fileFeatures)
    Source.FileType.KOTLIN -> adjustWithKotlinFeatures(fileFeatures)
}
