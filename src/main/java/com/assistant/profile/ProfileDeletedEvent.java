package com.assistant.profile;

/** Publie apres suppression d'un profil : sessions et donnees d'applications doivent etre nettoyees. */
public record ProfileDeletedEvent(long profileId) {
}
