package com.example.viewmodel

import com.example.model.ConfigBlock
import com.example.model.ConfigEditorState
import com.example.model.ConfigEditorSubTab
import com.example.model.ConfigItem
import com.example.model.ParseMode
import com.example.model.ScriptFunctionType
import com.example.network.ConfigTemplates
import com.example.network.LoliScriptParser
import java.util.UUID

object ConfigEditorHelper {

    fun createInitialState(existing: ConfigItem?): ConfigEditorState {
        if (existing != null) {
            val blocks = LoliScriptParser.parseToBlocks(existing.scriptPreview)
            return ConfigEditorState(
                configId = existing.id,
                isNew = false,
                name = existing.name,
                author = existing.author,
                category = existing.category,
                wordlistType = existing.wordlistType,
                needsProxies = existing.needsProxies,
                activeSubTab = ConfigEditorSubTab.STACKER,
                blocks = blocks,
                rawScript = existing.scriptPreview
            )
        }
        val preset = ConfigTemplates.presets.first()
        return ConfigEditorState(
            configId = UUID.randomUUID().toString(),
            isNew = true,
            name = "New Config",
            author = "LocalUser",
            category = preset.category,
            wordlistType = preset.wordlistType,
            needsProxies = preset.needsProxies,
            activeSubTab = ConfigEditorSubTab.STACKER,
            blocks = LoliScriptParser.parseToBlocks(preset.script),
            rawScript = preset.script
        )
    }

    fun applyPreset(state: ConfigEditorState, preset: ConfigTemplates.TemplatePreset): ConfigEditorState {
        return state.copy(
            name = if (state.name == "New Config" || state.name.isBlank()) preset.title else state.name,
            category = preset.category,
            wordlistType = preset.wordlistType,
            needsProxies = preset.needsProxies,
            blocks = LoliScriptParser.parseToBlocks(preset.script),
            rawScript = preset.script,
            debugResult = null
        )
    }

    fun switchSubTab(state: ConfigEditorState, target: ConfigEditorSubTab): ConfigEditorState {
        if (state.activeSubTab == target) return state
        return when (state.activeSubTab) {
            ConfigEditorSubTab.STACKER -> {
                val syncedScript = LoliScriptParser.serializeBlocks(state.blocks)
                state.copy(activeSubTab = target, rawScript = syncedScript)
            }
            ConfigEditorSubTab.SCRIPT -> {
                val syncedBlocks = LoliScriptParser.parseToBlocks(state.rawScript)
                state.copy(activeSubTab = target, blocks = syncedBlocks)
            }
            ConfigEditorSubTab.DEBUGGER -> state.copy(activeSubTab = target)
        }
    }

    fun syncScriptForSave(state: ConfigEditorState): String {
        return if (state.activeSubTab == ConfigEditorSubTab.STACKER) {
            LoliScriptParser.serializeBlocks(state.blocks)
        } else {
            state.rawScript
        }
    }

    fun createDefaultBlock(kind: String): ConfigBlock {
        val id = UUID.randomUUID().toString()
        return when (kind) {
            "REQUEST" -> ConfigBlock.RequestBlock(id = id)
            "PARSE" -> ConfigBlock.ParseBlock(id = id, mode = ParseMode.LR)
            "FUNCTION" -> ConfigBlock.FunctionBlock(id = id, functionType = ScriptFunctionType.BASE64_ENCODE)
            else -> ConfigBlock.KeyCheckBlock(id = id)
        }
    }
}
