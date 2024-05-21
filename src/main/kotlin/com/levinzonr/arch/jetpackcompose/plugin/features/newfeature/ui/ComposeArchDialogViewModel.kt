package com.levinzonr.arch.jetpackcompose.plugin.features.newfeature.ui

import com.intellij.openapi.application.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDirectory
import com.levinzonr.arch.jetpackcompose.plugin.core.*
import com.levinzonr.arch.jetpackcompose.plugin.features.ai.domain.FeatureBreakdownGenerator
import com.levinzonr.arch.jetpackcompose.plugin.features.ai.domain.models.FeatureBreakdown
import com.levinzonr.arch.jetpackcompose.plugin.features.newfeature.domain.repository.FeatureConfigurationRepository
import com.levinzonr.arch.jetpackcompose.plugin.features.newfeature.domain.models.FeatureProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class ComposeArchDialogViewModel(
    private val directory: PsiDirectory,
    private val generator: TemplateGenerator,
    private val repository: FeatureConfigurationRepository,
    private val editorManager: FileEditorManager,
    private val application: Application,
    private val featureBreakdownGenerator: FeatureBreakdownGenerator
) : BaseViewModel() {

    var name: String = ""
        get() = field.capitalize()

    val successFlow = MutableSharedFlow<Unit>()
    val errorFlow = MutableSharedFlow<String>()
    val loadingFlow = MutableSharedFlow<Boolean>()

    var description: String = ""

    var createFeaturePackage: Boolean = true

    fun onOkButtonClick() {
        scope.launch {
            if (description.isNotBlank()) {
                loadingFlow.emit(true)
                featureBreakdownGenerator.generate(name, description)
                    .onFailure { errorFlow.emit(it.message ?: "Unkown API Error") }
                    .onSuccess { breakdown ->
                        generateFiles(breakdown)
                    }
                loadingFlow.emit(false)
            } else {
                generateFiles(null)
            }
        }
    }

    private fun generateFiles(breakdown: FeatureBreakdown?) {
        val config = repository.get()
        val properties = FeatureProperties(name, config, breakdown).toProperties()
        invokeLater(ModalityState.defaultModalityState()) {
            runWriteAction {
                val featPackage =
                    if (createFeaturePackage) directory.createSubdirectory(name.lowercase()) else directory
                val file = generator.generateKt("ComposeContract", "${name}Contract", featPackage, properties)
                generator.generateKt("ComposeScreen", "${name}Screen", featPackage, properties)
                generator.generateKt("ComposeViewModel", "${name}ViewModel", featPackage, properties)
                generator.generateKt("ComposeCoordinator", "${name}Coordinator", featPackage, properties)
                generator.generateKt("ComposeRoute", "${name}Route", featPackage, properties)

                if (featPackage.findSubdirectory("components") == null) {
                    featPackage.createSubdirectory("components")
                }

                editorManager.openFile(file.virtualFile, true)
            }
            scope.launch { successFlow.emit(Unit) }
        }

    }

}