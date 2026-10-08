package fr.thibautperrin.tetrano.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.thibautperrin.tetrano.data.assets.ChallengeLevels
import fr.thibautperrin.tetrano.data.assets.ChallengeLevelsDataSource
import fr.thibautperrin.tetrano.data.assets.NeuralNetDataSource
import fr.thibautperrin.tetrano.data.assets.NeuralNets
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    @Singleton
    abstract fun bindChallengeLevels(impl: ChallengeLevelsDataSource): ChallengeLevels

    @Binds
    @Singleton
    abstract fun bindNeuralNets(impl: NeuralNetDataSource): NeuralNets
}
