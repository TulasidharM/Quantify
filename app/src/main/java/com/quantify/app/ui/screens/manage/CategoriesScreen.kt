package com.quantify.app.ui.screens.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.ui.viewmodel.TransactionViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: TransactionViewModel,
    onClose: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogParentId by remember { mutableStateOf<String?>(null) }
    var addDialogType by remember { mutableStateOf("EXPENSE") }

    val parentCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }
    }
    val childCategories = remember(categories) {
        categories.filter { it.parentId != null }.sortedBy { it.parentId ?: "" + it.name.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = {
                        addDialogParentId = null
                        addDialogType = "EXPENSE"
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(parentCategories, key = { it.id }) { parent ->
                val dotColor = if (parent.type == "INCOME") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                ListItem(
                    headlineContent = { Text(parent.name) },
                    supportingContent = { Text(parent.type) },
                    leadingContent = {
                        Canvas(modifier = Modifier.size(8.dp), onDraw = {
                            drawCircle(dotColor)
                        })
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                addDialogParentId = parent.id
                                addDialogType = parent.type
                                showAddDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add subcategory", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (!parent.isDefault) {
                                IconButton(onClick = { viewModel.deleteCategory(parent) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )
                val children = childCategories.filter { it.parentId == parent.id }
                val subDotColor = MaterialTheme.colorScheme.onSurfaceVariant
                children.forEach { child ->
                    ListItem(
                        headlineContent = { Text(child.name, modifier = Modifier.padding(start = 28.dp)) },
                        supportingContent = {
                            Text("Sub-category of " + parent.name, modifier = Modifier.padding(start = 28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        leadingContent = {
                            Canvas(modifier = Modifier.size(6.dp).padding(start = 14.dp), onDraw = {
                                drawCircle(subDotColor, radius = 3f)
                            })
                        },
                        trailingContent = {
                            if (!child.isDefault) {
                                IconButton(onClick = { viewModel.deleteCategory(child) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        NewCategoryDialog(
            onDismiss = {
                showAddDialog = false
                addDialogParentId = null
                addDialogType = "EXPENSE"
            },
            onSave = { name, type, parentId ->
                viewModel.addCategory(CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    color = 0xFF4C662B,
                    icon = "",
                    isDefault = false,
                    parentId = parentId
                ))
                showAddDialog = false
                addDialogParentId = null
                addDialogType = "EXPENSE"
            },
            parentCategories = categories,
            initialParentId = addDialogParentId,
            initialType = addDialogType
        )
    }
}
