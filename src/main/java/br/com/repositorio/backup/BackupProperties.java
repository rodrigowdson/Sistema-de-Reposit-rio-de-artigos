package br.com.repositorio.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    private boolean habilitado = false;
    private long intervaloMinutos = 60;
    private String ec2User = "admin";
    private String ec2Host = "";
    private String ec2Key = "";
    private String ec2Dir = "/home/admin/backup-repositorio";
    private long timeoutSegundos = 300;

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public long getIntervaloMinutos() {
        return intervaloMinutos;
    }

    public void setIntervaloMinutos(long intervaloMinutos) {
        this.intervaloMinutos = intervaloMinutos;
    }

    public String getEc2User() {
        return ec2User;
    }

    public void setEc2User(String ec2User) {
        this.ec2User = ec2User;
    }

    public String getEc2Host() {
        return ec2Host;
    }

    public void setEc2Host(String ec2Host) {
        this.ec2Host = ec2Host;
    }

    public String getEc2Key() {
        return ec2Key;
    }

    public void setEc2Key(String ec2Key) {
        this.ec2Key = ec2Key;
    }

    public String getEc2Dir() {
        return ec2Dir;
    }

    public void setEc2Dir(String ec2Dir) {
        this.ec2Dir = ec2Dir;
    }

    public long getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public void setTimeoutSegundos(long timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }
}
