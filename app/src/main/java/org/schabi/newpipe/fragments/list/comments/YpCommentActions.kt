/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.comments

import android.content.Context
import android.content.Intent
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.util.external_communication.ShareUtils

/** YewPlay: what the comment lists do with a comment or an error. */
object YpCommentActions {
    /** Copies the text of the comment (without the HTML). */
    fun copy(context: Context, comment: CommentsInfoItem) {
        val text = comment.commentText
        val plain = if (text.type() == Description.Type.HTML) {
            HtmlCompat.fromHtml(text.content(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else {
            text.content()
        }
        ShareUtils.copyToClipboard(context, plain)
    }

    /** Solves the reCAPTCHA, or opens the error report. */
    fun errorAction(fragment: Fragment, error: ErrorInfo) {
        val recaptchaUrl = error.recaptchaUrl
        if (recaptchaUrl != null) {
            val intent = Intent(fragment.requireContext(), ReCaptchaActivity::class.java)
                .putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, recaptchaUrl)
            fragment.startActivity(intent)
        } else {
            ErrorUtil.openActivity(fragment.requireContext(), error)
        }
    }

    fun openInBrowser(context: Context, error: ErrorInfo) {
        error.openInBrowserUrl?.let { ShareUtils.openUrlInBrowser(context, it) }
    }
}
