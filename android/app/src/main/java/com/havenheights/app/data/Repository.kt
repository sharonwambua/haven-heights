package com.havenheights.app.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ApiException(val code: Int, override val message: String) : Exception(message)

class Repository(private val api: HavenApi) {

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(ApiException(e.code(), errorMessage(e)))
    } catch (e: IOException) {
        Result.failure(ApiException(0, "Can't reach the server. Check your connection and try again."))
    } catch (e: Exception) {
        Result.failure(ApiException(-1, "The server sent something unexpected. Try again."))
    }

    private fun errorMessage(e: HttpException): String = try {
        val detail = JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject.get("detail")
        when {
            detail == null -> "Something went wrong."
            detail.isJsonPrimitive -> detail.asString
            // FastAPI validation errors arrive as a list of {loc, msg}
            detail.isJsonArray && detail.asJsonArray.size() > 0 ->
                detail.asJsonArray[0].asJsonObject.get("msg").asString
            else -> "Something went wrong."
        }
    } catch (_: Exception) {
        "Something went wrong (${e.code()})."
    }

    suspend fun login(phone: String, password: String) = call { api.login(LoginRequest(phone.trim(), password)) }
    suspend fun overview() = call { api.overview() }
    suspend fun tenants() = call { api.tenants() }
    suspend fun registerTenant(body: TenantCreate) = call { api.registerTenant(body) }
    suspend fun moveOut(id: Int) = call { api.moveOut(id) }
    suspend fun readings(id: Int) = call { api.readings(id) }
    suspend fun myUsage() = call { api.myUsage() }
    suspend fun requests() = call { api.requests() }
    suspend fun createRequest(body: NewRequest) = call { api.createRequest(body) }
    suspend fun updateRequest(id: Int, status: String, note: String?) =
        call { api.updateRequest(id, StatusUpdate(status, note)) }
    suspend fun announcements() = call { api.announcements() }
    suspend fun postAnnouncement(body: NewAnnouncement) = call { api.postAnnouncement(body) }
    suspend fun deleteAnnouncement(id: Int) = call { api.deleteAnnouncement(id) }

    suspend fun changePassword(current: String, new: String) = call { api.changePassword(ChangePasswordBody(current, new)) }
    suspend fun registerDevice(token: String) = call { api.registerDevice(DeviceBody(token)) }
    suspend fun unregisterDevice(token: String) = call { api.unregisterDevice(DeviceBody(token)) }
    suspend fun deleteReading(id: Int) = call { api.deleteReading(id) }

    suspend fun me() = call { api.me() }
    suspend fun updateMe(body: ProfileUpdate) = call { api.updateMe(body) }
    suspend fun updateEstate(body: EstateUpdate) = call { api.updateEstate(body) }
    suspend fun dashboard() = call { api.dashboard() }
    suspend fun bills() = call { api.bills() }
    suspend fun createBill(body: NewBill) = call { api.createBill(body) }
    suspend fun generateRent() = call { api.generateRent() }
    suspend fun setBillStatus(id: Int, status: String) = call { api.setBillStatus(id, BillStatusBody(status)) }
    suspend fun deleteBill(id: Int) = call { api.deleteBill(id) }
    suspend fun submitPayment(id: Int, reference: String) = call { api.submitPayment(id, SubmitPaymentBody(reference.trim())) }
    suspend fun reviewBill(id: Int, approve: Boolean, note: String?) =
        call { api.reviewBill(id, ReviewBody(approve, note?.trim()?.ifBlank { null })) }
    suspend fun inbox() = call { api.inbox() }
    suspend fun markInboxRead() = call { api.markInboxRead() }
    suspend fun updateLease(id: Int, body: LeaseUpdate) = call { api.updateLease(id, body) }
    suspend fun leaseFile(id: Int) = call { withContext(Dispatchers.IO) { api.leaseFile(id).bytes() } }
    suspend fun uploadLease(id: Int, name: String, mime: String, bytes: ByteArray) = call {
        api.uploadLease(id, MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaType())))
    }
    suspend fun photo(id: Int) = call { withContext(Dispatchers.IO) { api.photo(id).bytes() } }

    suspend fun scanReading(photo: File) = call {
        val part = MultipartBody.Part.createFormData(
            "photo", photo.name, photo.asRequestBody("image/jpeg".toMediaType()),
        )
        api.scanReading(part)
    }

    suspend fun saveReading(
        tenancyId: Int, value: Double, photo: File?, confirmJump: Boolean, recordedAt: String? = null,
        source: String = "MANUAL",
    ) = call {
        val text = "text/plain".toMediaType()
        val part = photo?.let {
            MultipartBody.Part.createFormData("photo", it.name, it.asRequestBody("image/jpeg".toMediaType()))
        }
        api.saveReading(
            tenancyId.toString().toRequestBody(text),
            value.toString().toRequestBody(text),
            confirmJump.toString().toRequestBody(text),
            source.toRequestBody(text),
            recordedAt?.toRequestBody(text),
            part,
        )
    }
}
