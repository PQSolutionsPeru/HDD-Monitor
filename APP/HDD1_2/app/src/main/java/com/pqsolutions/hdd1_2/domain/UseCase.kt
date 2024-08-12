package com.pqsolutions.hdd1_2.domain

interface UseCase<in Params, out Type> {
    suspend operator fun invoke(params: Params): Type
}