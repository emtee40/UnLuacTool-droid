package com.dingyi.unluactool.ui.editor.main

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.dingyi.unluactool.R
import com.dingyi.unluactool.base.BaseFragment
import com.dingyi.unluactool.common.ktx.dp
import com.dingyi.unluactool.databinding.FragmentEditorFileViewerBinding
import com.dingyi.unluactool.databinding.ItemEditorFileViewerListBinding
import com.dingyi.unluactool.databinding.ItemEditorFileViewerListDirBinding
import com.dingyi.unluactool.engine.filesystem.FileObjectType
import com.dingyi.unluactool.engine.filesystem.UnLuaCFileObject
import com.dingyi.unluactool.ui.editor.EditorViewModel
import io.github.dingyi222666.view.treeview.AbstractTree
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeEventListener
import io.github.dingyi222666.view.treeview.TreeNodeGenerator
import io.github.dingyi222666.view.treeview.TreeView
import io.github.dingyi222666.view.treeview.TreeViewBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileViewerFragment : BaseFragment<FragmentEditorFileViewerBinding>() {

    private val viewModel by activityViewModels<EditorViewModel>()

    private val treeViewData by lazy(LazyThreadSafetyMode.NONE) { Tree.createTree<UnLuaCFileObject>() }

    private val vfsManager by lazy(LazyThreadSafetyMode.NONE) {
       viewModel.vfsManager
    }

    private val projectUri by lazy(LazyThreadSafetyMode.NONE) {
        viewModel.project.value?.projectPath?.name?.friendlyURI ?: ""
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentEditorFileViewerBinding {
        return FragmentEditorFileViewerBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        treeViewData.apply {
            generator = FileDataGenerator()
            initTree()
        }

        (binding.editorFileViewerFragmentTreeView as TreeView<UnLuaCFileObject>).apply {
            val nodeBinder = FileNodeBinder()
            binder = nodeBinder
            tree = treeViewData
            nodeEventListener = nodeBinder
            bindCoroutineScope(lifecycleScope)
        }

        lifecycleScope.launch {
            refresh()
        }

    }

    private suspend fun refresh() {
        binding.editorFileViewerFragmentTreeView.isVisible = false
        binding.editorFileViewerFragmentProgressBar.isVisible = true
        binding.editorFileViewerFragmentTreeView.refresh()
        binding.editorFileViewerFragmentTreeView.isVisible = true
        binding.editorFileViewerFragmentProgressBar.isVisible = false
    }

    private fun openFileObject(fileObject: UnLuaCFileObject) {
        viewModel.openFileObject(fileObject)
    }

    inner class FileNodeBinder : TreeViewBinder<UnLuaCFileObject>(),
        TreeNodeEventListener<UnLuaCFileObject> {
        override fun areContentsTheSame(
            oldItem: TreeNode<UnLuaCFileObject>,
            newItem: TreeNode<UnLuaCFileObject>
        ): Boolean {
            return oldItem.data?.publicURIString == newItem.data?.publicURIString
        }

        override fun areItemsTheSame(
            oldItem: TreeNode<UnLuaCFileObject>,
            newItem: TreeNode<UnLuaCFileObject>
        ): Boolean {
            return oldItem.data?.publicURIString == newItem.data?.publicURIString
        }

        override fun bindView(
            holder: TreeView.ViewHolder,
            node: TreeNode<UnLuaCFileObject>,
            listener: TreeNodeEventListener<UnLuaCFileObject>
        ) {
            val itemView = holder.itemView
            val extra = checkNotNull(node.data)
            // val binding = ItemEditorFileViewerListBinding.bind(holder.currentItemView)


            val binding =
                if (extra.isFile) ItemEditorFileViewerListBinding.bind(holder.itemView)
                else ItemEditorFileViewerListDirBinding.bind(holder.itemView)

            val space = if (binding is ItemEditorFileViewerListDirBinding) binding.space else
                (binding as ItemEditorFileViewerListBinding).space

            space.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val leftMargin = if (!node.isChild) {
                    node.depth * 24.dp + 28.dp
                } else {
                    node.depth * 24.dp
                }
                this.width = leftMargin
            }


            val titleTextView = if (binding is ItemEditorFileViewerListDirBinding) binding.title
            else (binding as ItemEditorFileViewerListBinding).title

            val imageView = if (binding is ItemEditorFileViewerListDirBinding) binding.image
            else (binding as ItemEditorFileViewerListBinding).image

            val fileType = extra.getFileType()

            if (fileType == FileObjectType.FUNCTION) {
                titleTextView.text = node.name
            } else {
                applyDir(binding as ItemEditorFileViewerListDirBinding, extra, node)
            }

            when (fileType) {
                FileObjectType.DIR -> {
                    imageView.setImageResource(R.drawable.ic_baseline_folder_open_24)
                }

                FileObjectType.FILE -> {
                    imageView.setImageDrawable(
                        CircleDrawable(
                            "L", ContextCompat.getColor(requireContext(), R.color.blue_small_icon)
                        )
                    )
                }

                FileObjectType.FUNCTION, FileObjectType.FUNCTION_WITH_CHILD -> {
                    imageView.setImageDrawable(
                        CircleDrawable(
                            "F", ContextCompat.getColor(requireContext(), R.color.blue_small_icon)
                        )
                    )
                }
            }

        }


        private fun applyDir(
            binding: ItemEditorFileViewerListDirBinding,
            extra: UnLuaCFileObject,
            node: TreeNode<UnLuaCFileObject>
        ) {
            binding.title.text = node.name

            binding
                .arrow
                .animate()
                .rotation(if (node.expand) 90f else 0f)
                .setDuration(100)
                .start()
        }


        override fun onClick(node: TreeNode<UnLuaCFileObject>, holder: TreeView.ViewHolder) {
            val extra = checkNotNull(node.data)
            // val binding = ItemEditorFileViewerListBinding.bind(holder.itemView)

            if (!node.isChild) {
                openFileObject(extra)
                return
            }

            /*if (extra.getFileType() != FileObjectType.FUNCTION && !node.hasChild) {
                onToggle(node, !node.expand, holder)
            }*/

        }

        override fun onToggle(
            node: TreeNode<UnLuaCFileObject>,
            isExpand: Boolean,
            holder: TreeView.ViewHolder
        ) {
            val extra = checkNotNull(node.data)
            val binding = ItemEditorFileViewerListDirBinding.bind(holder.itemView)
            // if (extra.getFileType() != FileObjectType.FUNCTION) {
            applyDir(binding, extra, node)
            // }

            // node.expand = isExpand
        }

        override fun createView(parent: ViewGroup, viewType: Int): View {
            if (viewType == 1) {
                return ItemEditorFileViewerListDirBinding.inflate(
                    layoutInflater,
                    parent,
                    false
                ).root
            }
            return ItemEditorFileViewerListBinding.inflate(
                layoutInflater, parent, false
            ).root
        }

        override fun getItemViewType(node: TreeNode<UnLuaCFileObject>): Int {
            val extra = checkNotNull(node.data)
            return if (extra.isFile) 0 else 1
        }

    }

    inner class FileDataGenerator : TreeNodeGenerator<UnLuaCFileObject> {

        override suspend fun refreshNode(
            targetNode: TreeNode<UnLuaCFileObject>,
            oldChildNodeSet: Set<Int>,
            tree: AbstractTree<UnLuaCFileObject>
        ): Set<TreeNode<UnLuaCFileObject>> {

            val targetNodeExtra = checkNotNull(targetNode.data)
            // val friendlyURI = targetNodeExtra.name.friendlyURI

            if (targetNodeExtra.getFileType() == FileObjectType.FUNCTION) {
                targetNode.isChild = false
                return setOf()
            }

            val oldNodes = tree.getNodes(oldChildNodeSet)

            val child =
                withContext(Dispatchers.IO) {
                    checkNotNull(targetNodeExtra.children)
                        // .filter { it.name.friendlyURI != friendlyURI }
                        .toMutableList()
                }

            val result = mutableSetOf<TreeNode<UnLuaCFileObject>>()

            oldNodes.forEach { node ->
                val virtualFile =
                    child.find { it.name.friendlyURI == node.data?.name?.friendlyURI }
                if (virtualFile != null) {
                    result.add(node)
                }
                child.remove(virtualFile)
            }

            if (child.isEmpty()) {
                return result
            }

            child.forEach {
                val unLuaCFileObject = it as UnLuaCFileObject
                val hasChild =
                    if (unLuaCFileObject.getFileType() != FileObjectType.FUNCTION) false else {
                        withContext(Dispatchers.IO) { unLuaCFileObject.children }.isEmpty()
                    }
                result.add(
                    TreeNode(
                        unLuaCFileObject,
                        targetNode.depth + 1,
                        unLuaCFileObject.name.baseName.replace(".lasm", ".lua"),
                        tree.generateId(),
                        hasChild,
                        unLuaCFileObject.getFileType() != FileObjectType.FUNCTION,
                        false
                    )
                )
            }

            return result
        }


        override fun createRootNode(): TreeNode<UnLuaCFileObject> {
            val project = checkNotNull(viewModel.project.value)
            val rootFileObject = vfsManager.resolveFile("unluac://${project.name}")
            return TreeNode(
                data = rootFileObject as UnLuaCFileObject,
                depth = -1,
                name = project.name,
                id = 0,
                hasChild = true,
                isChild = true,
                expand = true
            )
        }

    }


    internal class CircleDrawable(
        private val mDrawText: String,
        private val mDrawBackgroundColor: Int,
        private val mDrawTextColor: Int = mDrawBackgroundColor,
    ) : Drawable() {
        private val mPaint = Paint().apply {
            isAntiAlias = true
            color = mDrawBackgroundColor
            strokeWidth = (Resources.getSystem()
                .displayMetrics.density * 1.5).toFloat()
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        private val mTextPaint = Paint().apply {
            color = -0x1
            isAntiAlias = true
            textSize = Resources.getSystem()
                .displayMetrics.density * 10
            textAlign = Paint.Align.CENTER
            color = mDrawTextColor
        }

        override fun draw(canvas: Canvas) {
            val width = bounds.right.toFloat()
            val height = bounds.bottom.toFloat()

            // canvas.drawCircle(width / 2, height / 2, width / 2, mPaint)

            // canvas.drawRect(0f, 0f, width, height, mPaint)
            canvas.drawArc(
                0f + mPaint.strokeWidth, 0f + mPaint.strokeWidth, width
                        - mPaint.strokeWidth, height - mPaint.strokeWidth, 0f, 360f, false, mPaint
            )
            canvas.save()
            canvas.translate(width / 2f, height / 2f)
            val textCenter = -(mTextPaint.descent() + mTextPaint.ascent()) / 2f
            canvas.drawText(mDrawText, 0f, textCenter, mTextPaint)
            canvas.restore()
        }

        override fun setAlpha(p1: Int) {
            mPaint.alpha = p1
            mTextPaint.alpha = p1
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            mTextPaint.colorFilter = colorFilter
        }

        @Deprecated(
            "Deprecated in Java",
            ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat")
        )
        override fun getOpacity(): Int {
            return PixelFormat.OPAQUE
        }


    }


}
