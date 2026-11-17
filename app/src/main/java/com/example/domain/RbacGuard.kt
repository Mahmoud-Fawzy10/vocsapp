package com.example.domain

import com.example.data.local.WorkspaceEntity
import com.example.data.local.WorkspaceMemberEntity
import com.example.model.MemberStatus
import com.example.model.WorkspaceRole

class UnauthorizedActionException(message: String) : SecurityException(message)

object RbacGuard {
    fun requireActiveMember(member: WorkspaceMemberEntity?): WorkspaceRole {
        if (member == null) {
            throw UnauthorizedActionException("غير مصرح: المستخدم ليس عضواً في مساحة العمل.")
        }
        if (MemberStatus.safeValueOf(member.status) != MemberStatus.ACTIVE) {
            throw UnauthorizedActionException("غير مصرح: حساب العضو معلّق حالياً.")
        }
        return WorkspaceRole.safeValueOf(member.role)
    }

    fun requireEditVocab(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canEditVocab()) {
            throw UnauthorizedActionException("غير مصرح: دورك (${role.arabicLabel}) لا يسمح بإضافة أو تعديل المفردات.")
        }
    }

    fun requireDeleteVocab(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canDeleteVocab()) {
            throw UnauthorizedActionException("غير مصرح: حذف المفردات مقتصر على مالك المساحة.")
        }
    }

    fun requireReview(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canReview()) {
            throw UnauthorizedActionException("غير مصرح: اعتماد أو رفض المقترحات مقتصر على المراجع أو المالك.")
        }
    }

    fun requireManageUsers(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canManageUsers()) {
            throw UnauthorizedActionException("غير مصرح: إدارة المستخدمين والأدوار مقتصرة على مالك المساحة.")
        }
    }

    fun requireManageSettings(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canManageSettings()) {
            throw UnauthorizedActionException("غير مصرح: تعديل إعدادات مساحة العمل مقتصر على المالك.")
        }
    }

    fun requireManageBooks(member: WorkspaceMemberEntity?) {
        val role = requireActiveMember(member)
        if (!role.canManageBooks()) {
            throw UnauthorizedActionException("غير مصرح: إدارة الكتب والمناهج مقتصرة على المالك.")
        }
    }

    fun requireImport(member: WorkspaceMemberEntity?, workspace: WorkspaceEntity?) {
        val role = requireActiveMember(member)
        val allowEditor = workspace?.allowEditorImport ?: false
        if (!role.canImport(allowEditor)) {
            throw UnauthorizedActionException("غير مصرح: الاستيراد غير مسموح لدور (${role.arabicLabel}).")
        }
    }

    fun requireExport(member: WorkspaceMemberEntity?, workspace: WorkspaceEntity?) {
        val role = requireActiveMember(member)
        val allowViewer = workspace?.allowViewerExport ?: false
        if (!role.canExport(allowViewer)) {
            throw UnauthorizedActionException("غير مصرح: التصدير غير مفعّل للمشاهدين في إعدادات المساحة.")
        }
    }
}
