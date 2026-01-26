package com.expensetracker.app.ui.categories

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.ui.components.AvailableColors
import com.expensetracker.app.ui.components.AvailableIcons
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.components.getIconForName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    onShowPremium: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoriesState by viewModel.categoriesState.collectAsState()
    val expandedCategories by viewModel.expandedCategories.collectAsState()
    val context = LocalContext.current

    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CategoryEvent.CategorySaved -> {
                    Toast.makeText(context, "Category saved", Toast.LENGTH_SHORT).show()
                }
                is CategoryEvent.CategoryDeleted -> {
                    Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                }
                is CategoryEvent.ShowPremiumRequired -> {
                    onShowPremium()
                }
                is CategoryEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!categoriesState.isPremium) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Free Plan",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${categoriesState.categories.size}/${CategoriesViewModel.FREE_CATEGORY_LIMIT} categories used",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            TextButton(onClick = onShowPremium) {
                                Text("Upgrade")
                            }
                        }
                    }
                }
            }

            // Display hierarchical categories
            categoriesState.rootCategories.forEach { category ->
                val hasSubcategories = categoriesState.subcategoriesMap.containsKey(category.id)
                val isExpanded = category.id in expandedCategories

                item(key = category.id) {
                    CategoryListItem(
                        category = category,
                        hasSubcategories = hasSubcategories,
                        isExpanded = isExpanded,
                        onToggleExpand = { viewModel.toggleCategoryExpanded(category.id) },
                        onEdit = { viewModel.showEditDialog(category) },
                        onDelete = { categoryToDelete = category },
                        onAddSubcategory = { viewModel.showAddDialog(parentCategoryId = category.id) }
                    )
                }

                // Show subcategories when expanded
                if (hasSubcategories && isExpanded) {
                    val subcategories = categoriesState.subcategoriesMap[category.id] ?: emptyList()
                    subcategories.forEach { subcategory ->
                        item(key = subcategory.id) {
                            SubcategoryListItem(
                                category = subcategory,
                                onEdit = { viewModel.showEditDialog(subcategory) },
                                onDelete = { categoryToDelete = subcategory }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (uiState.showDialog) {
        CategoryDialog(
            isEditing = uiState.editingCategory != null,
            name = uiState.dialogName,
            icon = uiState.dialogIcon,
            color = uiState.dialogColor,
            onNameChange = viewModel::updateDialogName,
            onIconChange = viewModel::updateDialogIcon,
            onColorChange = viewModel::updateDialogColor,
            onSave = viewModel::saveCategory,
            onDismiss = viewModel::hideDialog
        )
    }

    // Delete Confirmation Dialog
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category") },
            text = {
                Text("Are you sure you want to delete \"${category.name}\"? Transactions using this category will become uncategorized.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(category)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryListItem(
    category: Category,
    hasSubcategories: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddSubcategory: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse button for categories that have or can have subcategories
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (hasSubcategories)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            CategoryIcon(
                icon = category.icon,
                color = category.color
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (category.isDefault) {
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Add subcategory button
            IconButton(onClick = onAddSubcategory) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    contentDescription = "Add Subcategory",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun SubcategoryListItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SubdirectoryArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            CategoryIcon(
                icon = category.icon,
                color = category.color,
                size = 36.dp,
                iconSize = 18.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryDialog(
    isEditing: Boolean,
    name: String,
    icon: String,
    color: Color,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Category" else "New Category")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CategoryIcon(
                        icon = icon,
                        color = color,
                        size = 64.dp,
                        iconSize = 32.dp
                    )
                }

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Icon Selector
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvailableIcons) { iconName ->
                        val isSelected = iconName == icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        color,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { onIconChange(iconName) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconForName(iconName),
                                contentDescription = null,
                                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Color Selector
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvailableColors) { colorOption ->
                        val isSelected = colorOption == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorOption)
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { onColorChange(colorOption) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
