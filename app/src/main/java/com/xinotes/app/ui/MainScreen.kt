package com.xinotes.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NotesViewModel, initialNoteId: Long?) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialNoteId) {
        if (initialNoteId != null) viewModel.selectNote(initialNoteId)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    uiState = uiState,
                    onNoteSelected = { id ->
                        viewModel.selectNote(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewNote = {
                        viewModel.newNote { id ->
                            viewModel.selectNote(id)
                            scope.launch { drawerState.close() }
                        }
                    },
                    onDeleteNote = viewModel::deleteNote,
                    onRenameNote = viewModel::renameNote,
                    onCreateGroup = viewModel::createGroup,
                    onDeleteGroup = viewModel::deleteGroup,
                    onRenameGroup = viewModel::renameGroup,
                    onMoveNoteToGroup = viewModel::moveNoteToGroup,
                    onReorderNotes = viewModel::reorderNotes
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ξ-notes") },
                    navigationIcon = {
                        // Кнопка-гамбургер (три полоски) — открывает/закрывает панель,
                        // так же как и свайп от левого края экрана.
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                        }
                    }
                )
            }
        ) { padding ->
            val selectedNote = uiState.notes.find { it.id == uiState.selectedNoteId }
            Box(modifier = Modifier.padding(padding)) {
                if (selectedNote != null) {
                    NoteEditorScreen(
                        note = selectedNote,
                        onSave = viewModel::saveNote,
                        onSetReminder = viewModel::setReminder,
                        onClearReminder = viewModel::clearReminder
                    )
                } else {
                    EmptyState(onNewNote = {
                        viewModel.newNote { id -> viewModel.selectNote(id) }
                    })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onNewNote: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Нет открытой заметки")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNewNote) { Text("Создать заметку") }
    }
}
