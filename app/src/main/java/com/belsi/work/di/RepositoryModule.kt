package com.belsi.work.di

import com.belsi.work.data.repositories.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository
    
    @Binds
    @Singleton
    abstract fun bindShiftRepository(
        shiftRepositoryImpl: ShiftRepositoryImpl
    ): ShiftRepository
    
    @Binds
    @Singleton
    abstract fun bindPhotoRepository(
        photoRepositoryImpl: PhotoRepositoryImpl
    ): PhotoRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(
        teamRepositoryImpl: TeamRepositoryImpl
    ): TeamRepository

    @Binds
    @Singleton
    abstract fun bindCuratorRepository(
        curatorRepositoryImpl: CuratorRepositoryImpl
    ): CuratorRepository

    @Binds
    @Singleton
    abstract fun bindWalletRepository(
        walletRepositoryImpl: WalletRepositoryImpl
    ): WalletRepository

    // FIX(2026-06-16) Ф3: WalletF3Repository — часы/метры + штраф/бонус + выплаты Rocket Work
    @Binds
    @Singleton
    abstract fun bindWalletF3Repository(
        walletF3RepositoryImpl: WalletF3RepositoryImpl
    ): WalletF3Repository

    @Binds
    @Singleton
    abstract fun bindSupportRepository(
        supportRepositoryImpl: SupportRepositoryImpl
    ): SupportRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindTicketRepository(
        ticketRepositoryImpl: TicketRepositoryImpl
    ): TicketRepository

    @Binds
    @Singleton
    abstract fun bindInviteRepository(
        inviteRepositoryImpl: InviteRepositoryImpl
    ): InviteRepository

    @Binds
    @Singleton
    abstract fun bindToolsRepository(
        toolsRepositoryImpl: ToolsRepositoryImpl
    ): ToolsRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindPushRepository(
        pushRepositoryImpl: PushRepositoryImpl
    ): PushRepository

    @Binds
    @Singleton
    abstract fun bindMessengerRepository(
        messengerRepositoryImpl: MessengerRepositoryImpl
    ): MessengerRepository

    @Binds
    @Singleton
    abstract fun bindObjectsRepository(
        objectsRepositoryImpl: ObjectsRepositoryImpl
    ): ObjectsRepository

    @Binds
    @Singleton
    abstract fun bindPauseRepository(
        pauseRepositoryImpl: PauseRepositoryImpl
    ): PauseRepository

    @Binds
    @Singleton
    abstract fun bindCoordinatorRepository(
        coordinatorRepositoryImpl: CoordinatorRepositoryImpl
    ): CoordinatorRepository

    // FIX(2026-05-05): BatchRepository для Pipeline партии
    @Binds
    @Singleton
    abstract fun bindBatchRepository(
        batchRepositoryImpl: com.belsi.work.data.repositories.BatchRepositoryImpl
    ): com.belsi.work.data.repositories.BatchRepository

    // FIX(2026-05-06): ProductionRepository для Brigade/Facility/Materials/Engineer
    @Binds
    @Singleton
    abstract fun bindProductionRepository(
        impl: com.belsi.work.data.repositories.ProductionRepositoryImpl
    ): com.belsi.work.data.repositories.ProductionRepository

    // FIX(2026-05-10): AiRepository для всех 7 AI endpoints (BELSI 1.3.0)
    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: com.belsi.work.data.repositories.AiRepositoryImpl
    ): com.belsi.work.data.repositories.AiRepository

    // FIX(2026-05-11) BELSI 2.0.0: UpdateGateRepository — Version+Audit для экрана обновления
    @Binds
    @Singleton
    abstract fun bindUpdateGateRepository(
        impl: com.belsi.work.data.repositories.UpdateGateRepositoryImpl
    ): com.belsi.work.data.repositories.UpdateGateRepository
}
