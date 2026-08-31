package org.joget.mokxa.model;

public class ClientIntegrationOptions {

    private String teamsSyncMode;
    private String teamsUrl;
    private String sharePointSiteUrl;

    private String qbSyncMode;
    private String qbCustomerId;

    public String getTeamsSyncMode() { return teamsSyncMode; }
    public void setTeamsSyncMode(String teamsSyncMode) { this.teamsSyncMode = teamsSyncMode; }

    public String getTeamsUrl() { return teamsUrl; }
    public void setTeamsUrl(String teamsUrl) { this.teamsUrl = teamsUrl; }

    public String getSharePointSiteUrl() { return sharePointSiteUrl; }
    public void setSharePointSiteUrl(String sharePointSiteUrl) { this.sharePointSiteUrl = sharePointSiteUrl; }

    public String getQbSyncMode() { return qbSyncMode; }
    public void setQbSyncMode(String qbSyncMode) { this.qbSyncMode = qbSyncMode; }

    public String getQbCustomerId() { return qbCustomerId; }
    public void setQbCustomerId(String qbCustomerId) { this.qbCustomerId = qbCustomerId; }
}