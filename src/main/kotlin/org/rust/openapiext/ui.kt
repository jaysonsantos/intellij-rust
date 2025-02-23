/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.RowBuilder
import com.intellij.util.Alarm
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.RsElement
import javax.swing.event.DocumentEvent
import kotlin.reflect.KProperty

class UiDebouncer(
    private val parentDisposable: Disposable,
    private val delayMillis: Int = 200
) {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    /**
     * @param onUiThread: callback to be executed in EDT with **any** modality state.
     * Use it only for UI updates
     */
    fun <T> run(onPooledThread: () -> T, onUiThread: (T) -> Unit) {
        if (Disposer.isDisposed(parentDisposable)) return
        alarm.cancelAllRequests()
        alarm.addRequest({
            val r = onPooledThread()
            invokeLater(ModalityState.any()) {
                if (!Disposer.isDisposed(parentDisposable)) {
                    onUiThread(r)
                }
            }
        }, delayMillis)
    }
}

fun pathToDirectoryTextField(
    disposable: Disposable,
    @Suppress("UnstableApiUsage") @DialogTitle title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton =
    pathTextField(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        disposable,
        title,
        onTextChanged
    )

fun pathToRsFileTextField(
    disposable: Disposable,
    @DialogTitle title: String,
    project: Project,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton =
    pathTextField(
        FileChooserDescriptorFactory
            .createSingleFileDescriptor(RsFileType)
            .withRoots(project.guessProjectDir()),
        disposable,
        title,
        onTextChanged
    )

fun pathTextField(
    fileChooserDescriptor: FileChooserDescriptor,
    disposable: Disposable,
    @DialogTitle title: String,
    onTextChanged: () -> Unit = {}
): TextFieldWithBrowseButton {

    val component = TextFieldWithBrowseButton(null, disposable)
    component.addBrowseFolderListener(
        title, null, null,
        fileChooserDescriptor,
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
    )
    component.childComponent.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            onTextChanged()
        }
    })

    return component
}

class CheckboxDelegate(private val checkbox: JBCheckBox) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return checkbox.isSelected
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        checkbox.isSelected = value
    }
}

fun RowBuilder.row(
    labelText: String,
    toolTip: String,
    separated: Boolean = false,
    init: Row.() -> Unit
): Row {
    val label = Label(labelText)
    label.toolTipText = toolTip.trimIndent()
    return row(label, separated, init)
}

fun selectElement(element: RsElement, editor: Editor) {
    val start = element.textRange.startOffset
    editor.caretModel.moveToOffset(start)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.setSelection(start, element.textRange.endOffset)
}
