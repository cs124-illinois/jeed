package edu.illinois.cs.cs125.jeed.core

private fun CoverageResult.adjustWithJavaFeatures(sourceFeatures: FeaturesResults): CoverageResult {
    val byFile = byFile.mapValues { (filename, coverageMap) ->
        val featureMap = sourceFeatures.lookup("", filename).features.featureList.toLineMap()
        coverageMap.mapValues inner@{ (line, coverage) ->
            if (coverage == LineCoverage.COVERED || coverage == LineCoverage.EMPTY) {
                return@inner coverage
            }
            check(coverage in listOf(LineCoverage.NOT_COVERED, LineCoverage.PARTLY_COVERED)) {
                "Invalid coverage value: $coverage"
            }
            val features = featureMap[line]?.map { it.feature } ?: return@inner coverage
            when {
                features.contains(FeatureName.CLASS) -> LineCoverage.IGNORED
                features.contains(FeatureName.ASSERT) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
                else -> coverage
            }
        }
    }
    return CoverageResult(byFile, byClass)
}

private fun CoverageResult.adjustWithKotlinFeatures(sourceFeatures: FeaturesResults): CoverageResult {
    val byFile = byFile.mapValues { (filename, coverageMap) ->
        val featureMap = sourceFeatures.lookup("", filename).features.featureList.toLineMap()
        coverageMap.mapValues inner@{ (line, coverage) ->
            if (coverage == LineCoverage.COVERED || coverage == LineCoverage.EMPTY) {
                return@inner coverage
            }
            check(coverage in listOf(LineCoverage.NOT_COVERED, LineCoverage.PARTLY_COVERED)) {
                "Invalid coverage value: $coverage"
            }
            val features = featureMap[line]?.map { it.feature } ?: return@inner coverage
            when {
                features.contains(FeatureName.ASSERT) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
                features.contains(FeatureName.FOR_LOOP_STEP) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
                features.contains(FeatureName.FOR_LOOP_RANGE) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
                features.contains(FeatureName.ELVIS_OPERATOR) && coverage == LineCoverage.PARTLY_COVERED -> LineCoverage.IGNORED
                features.contains(FeatureName.HAS_COMPANION_OBJECT) && coverage == LineCoverage.NOT_COVERED -> LineCoverage.IGNORED
                else -> coverage
            }
        }
    }
    return CoverageResult(byFile, byClass)
}
fun CoverageResult.adjustWithFeatures(sourceFeatures: FeaturesResults, language: Source.FileType) = when (language) {
    Source.FileType.JAVA -> adjustWithJavaFeatures(sourceFeatures)
    Source.FileType.KOTLIN -> adjustWithKotlinFeatures(sourceFeatures)
}
