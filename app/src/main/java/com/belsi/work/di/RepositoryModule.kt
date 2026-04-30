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
}
