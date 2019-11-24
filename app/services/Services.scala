package services

import javax.inject.{Inject, Singleton}

@Singleton
class Services @Inject()(
    val discordService: DiscordService,
    val bnetService: BnetService,
    val vision: Vision
)
