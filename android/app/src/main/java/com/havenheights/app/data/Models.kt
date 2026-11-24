package com.havenheights.app.data

// Field names are mapped from snake_case by Gson (see ApiFactory).

data class LoginRequest(val phone: String, val password: String)

data class AuthResponse(val accessToken: String, val role: String, val name: String, val tenancyId: Int?)

data class UserInfo(val role: String, val name: String, val tenancyId: Int?) {
    val isLandlord get() = role == "LANDLORD"
}

data class Tenant(
    val tenancyId: Int,
    val name: String,
    val phone: String,
    val unitNumber: String,
    val unitType: String,
    val moveInDate: String,
    val initialMeterReading: Double,
    val latestReading: Double,
    val active: Boolean,
    val email: String?,
    val emergencyContact: String?,
    val monthlyRent: Double,
    val deposit: Double,
    val rentDueDay: Int,
    val leaseEnd: String?,
    val leaseTerms: String?,
    val hasLeaseFile: Boolean,
    val leaseFileName: String?,
)

data class TenantCreate(
    val name: String,
    val phone: String,
    val unitNumber: String,
    val unitType: String,
    val moveInDate: String,
    val initialMeterReading: Double,
    val monthlyRent: Double,
    val deposit: Double,
    val rentDueDay: Int = 5,
)

data class LeaseUpdate(
    val monthlyRent: Double,
    val deposit: Double,
    val rentDueDay: Int,
    val leaseEnd: String?,
    val leaseTerms: String?,
)

data class TenantCreated(val tenant: Tenant, val temporaryPassword: String)

data class Reading(
    val id: Int,
    val tenancyId: Int,
    val value: Double,
    val previousValue: Double,
    val unitsUsed: Double,
    val cost: Double,
    val source: String,
    val hasPhoto: Boolean,
    val createdAt: String,
)

data class ReadingSuggestion(
    val value: Double,
    val confidence: Double,
    val perDigitConfidence: List<Double>,
    val digits: String,
)

data class MyUsage(
    val unitNumber: String,
    val ratePerUnit: Double,
    val latestReading: Double,
    val monthUnits: Double,
    val monthCost: Double,
    val readings: List<Reading>,
)

data class MaintenanceRequest(
    val id: Int,
    val tenancyId: Int,
    val tenantName: String,
    val unitNumber: String,
    val title: String,
    val description: String,
    val urgency: String,   // LOW | MEDIUM | HIGH
    val status: String,    // OPEN | IN_PROGRESS | RESOLVED
    val landlordNote: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class NewRequest(val title: String, val description: String, val urgency: String)

data class StatusUpdate(val status: String, val landlordNote: String?)

data class Announcement(
    val id: Int,
    val title: String,
    val body: String,
    val category: String,
    val createdAt: String,
)

data class NewAnnouncement(val title: String, val body: String, val category: String)

data class Overview(
    val tenants: Int,
    val vacantUnits: Int,
    val openRequests: Int,
    val highUrgencyOpen: Int,
    val readingsThisMonth: Int,
    val pendingBillsTotal: Double,
    val overdueBills: Int,
    val awaitingApproval: Int,
)

data class Estate(
    val estateName: String,
    val address: String,
    val paymentInfo: String,
    val landlordName: String,
    val landlordPhone: String,
)

data class EstateUpdate(val estateName: String, val address: String, val paymentInfo: String)

data class Me(
    val role: String,
    val name: String,
    val phone: String,
    val email: String?,
    val emergencyContact: String?,
    val tenant: Tenant?,
    val estate: Estate,
)

data class ProfileUpdate(val name: String?, val email: String?, val emergencyContact: String?)

data class Bill(
    val id: Int,
    val tenancyId: Int,
    val tenantName: String,
    val unitNumber: String,
    val kind: String,      // RENT | WATER | OTHER
    val description: String,
    val amount: Double,
    val dueDate: String,
    val status: String,    // PENDING | SUBMITTED (tenant says paid, awaiting approval) | PAID
    val overdue: Boolean,
    val paidAt: String?,
    val createdAt: String,
    val paymentReference: String?,
    val submittedAt: String?,
    val reviewNote: String?,
)

data class NewBill(val tenancyId: Int, val kind: String, val description: String, val amount: Double, val dueDate: String)

data class BillStatusBody(val status: String)

data class SubmitPaymentBody(val reference: String)

data class ReviewBody(val approve: Boolean, val note: String?)

data class RentRun(val created: Int)

data class AppNotification(
    val id: Int,
    val title: String,
    val body: String,
    val kind: String,
    val read: Boolean,
    val createdAt: String,
)

data class Inbox(val unread: Int, val items: List<AppNotification>)

data class Dashboard(
    val tenant: Tenant,
    val estate: Estate,
    val pendingTotal: Double,
    val awaitingTotal: Double,
    val overdueTotal: Double,
    val pendingBills: List<Bill>,
    val latestNotices: List<Announcement>,
    val openRequests: Int,
    val unreadNotifications: Int,
    val waterMonthUnits: Double,
    val waterMonthCost: Double,
)

data class DeviceBody(val token: String)

data class ChangePasswordBody(val currentPassword: String, val newPassword: String)

/** A reading entered with no signal, waiting to be uploaded. */
data class PendingReading(
    val tenancyId: Int,
    val unitNumber: String,
    val value: Double,
    val confirmJump: Boolean,
    val recordedAt: String,
    val source: String? = "MANUAL",
)

const val KIND_READING = "READING"
const val KIND_REQUEST = "REQUEST"

sealed interface SaveResult {
    /** Saved on this phone; it uploads when the connection returns. */
    data object Queued : SaveResult
    data class Saved(val reading: Reading) : SaveResult
    data class NeedsConfirm(val message: String) : SaveResult
    data class Failed(val message: String) : SaveResult
}
