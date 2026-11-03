package com.assistant.auth;

/** Publie quand une session d'un profil est fermee (deconnexion ou bascule vers un autre profil sur le meme appareil). */
public record ProfileSignedOutEvent(long profileId) {
}
