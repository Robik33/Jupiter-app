package com.marketia.jupiter.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketia.jupiter.data.entity.*
import com.marketia.jupiter.data.repository.JupiterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: JupiterRepository
) : ViewModel() {

    val links    = repository.links.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = repository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val systems  = repository.systems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val agents   = repository.agents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val nodes    = repository.nodes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val edges    = repository.edges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addLink(url: String, title: String, category: String) {
        viewModelScope.launch { repository.saveLink(url, title, category) }
    }

    fun addProject(name: String, type: String, description: String) {
        viewModelScope.launch { repository.addProject(name, type, description) }
    }

    fun addSystem(name: String, type: String, architecture: String) {
        viewModelScope.launch { repository.addSystem(name, type, architecture) }
    }

    fun addAgent(name: String, model: String, capability: String) {
        viewModelScope.launch { repository.addAgent(name, model, capability) }
    }

    fun deleteNode(node: MemoryNodeEntity) {
        viewModelScope.launch { repository.deleteMemoryNode(node) }
    }
}
