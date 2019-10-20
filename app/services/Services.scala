package services

import javax.inject.{Inject, Singleton}

@Singleton
class Services @Inject()(
    val discordService: DiscordService,
    val wowheadService: WowheadService
)
