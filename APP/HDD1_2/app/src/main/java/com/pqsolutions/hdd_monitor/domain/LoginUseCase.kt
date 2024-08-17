package com.pqsolutions.hdd_monitor.domain

import com.pqsolutions.hdd_monitor.data.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<LoginUseCase.Params, Result<Unit>> {

    override suspend fun invoke(params: Params): Result<Unit> {
        return authRepository.login(params.email, params.password)
    }

    data class Params(val email: String, val password: String)
}