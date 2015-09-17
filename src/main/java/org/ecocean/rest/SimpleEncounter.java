package org.ecocean.rest;

import java.time.LocalDate;
import java.time.OffsetTime;

import org.ecocean.Location;

class SimpleEncounter
{
    private int id;

    private LocalDate encdate;
    private OffsetTime starttime;
    private OffsetTime endtime;

    private Location location;

    private SimpleIndividual individual;

    public SimpleEncounter()
    {
        // for deserialization
    }

    public SimpleEncounter(final int id,
                           final LocalDate encdate)
    {
        this.id = id;
        this.encdate = encdate;
    }

    public int getId() {
        return id;
    }

    public LocalDate getEncDate() {
        return encdate;
    }

    public void setEncDate(final LocalDate encdate) {
        this.encdate = encdate;
    }

    public SimpleIndividual getIndividual() {
        return individual;
    }

    public void setIndividual(final SimpleIndividual individual) {
        this.individual = individual;
    }

    public OffsetTime getStarttime() {
        return starttime;
    }

    public void setStarttime(final OffsetTime starttime) {
        this.starttime = starttime;
    }

    public OffsetTime getEndtime() {
        return endtime;
    }

    public void setEndtime(final OffsetTime endtime) {
        this.endtime = endtime;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(final Location location) {
        this.location = location;
    }
}