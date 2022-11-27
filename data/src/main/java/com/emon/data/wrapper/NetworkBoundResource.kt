package com.emon.data.wrapper

import android.app.Application
import com.google.gson.JsonParser
import com.emon.data.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import com.emon.domain.base.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class NetworkBoundResource @Inject constructor(
    private val application: Application
){
    private  val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun<ResultType> downloadData(api : suspend () -> Response<ResultType>): Flow<Result<ResultType>> {
        return withContext(ioDispatcher) {
            flow {
                emit(Result.Loading(true))
                val response:Response<ResultType> = api()
                emit(Result.Loading(false))
                if (response.isSuccessful){
                    response.body()?.let {
                        emit(Result.Success(data = it))
                    }?: emit(Result.Error(message = "Unknown error occurred", code = 0))
                }else{
                    emit(Result.Error(message = parserErrorBody(response.errorBody()), code = response.code()))
                }

            }.catch { error->
                Timber.e(error.localizedMessage)
                emit(Result.Loading(false))
                delay(5)
                emit(Result.Error(message = message(error), code = code(error)))
            }
        }
    }

    private fun parserErrorBody(response: ResponseBody?):String{
        return response?.let {
            val errorMessage = JsonParser.parseString(it.string()).asJsonObject["message"].asString
            errorMessage.ifEmpty { application.getString(R.string.message_whoops_something_went_wrong) }
            errorMessage
        }?:application.getString(R.string.message_unknown_error_occur)
    }
    private fun message(throwable: Throwable?):String{
        when (throwable) {
            is SocketTimeoutException -> return application.getString(R.string.message_whoops_connection_time_out)
            is IOException -> return application.getString(R.string.message_please_check_your_internet_connection)
            is HttpException -> return try {
                val errorJsonString = throwable.response()?.errorBody()?.string()
                val errorMessage = JsonParser.parseString(errorJsonString).asJsonObject["message"].asString
                errorMessage.ifEmpty { application.getString(R.string.message_whoops_something_went_wrong) }
            }catch (e:Exception){
                application.getString(R.string.message_unknown_error_occur)
            }
        }
        return application.getString(R.string.message_unknown_error_occur)
    }
    private fun code(throwable: Throwable?):Int{
        return if (throwable is HttpException) (throwable).code()
        else  0
    }
}