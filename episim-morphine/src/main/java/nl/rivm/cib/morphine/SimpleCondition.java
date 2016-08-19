/* $Id$
 * 
 * Part of ZonMW project no. 50-53000-98-156
 * 
 * @license
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright (c) 2016 RIVM National Institute for Health and Environment 
 */
package nl.rivm.cib.morphine;

import java.util.Objects;

import io.coala.exception.ExceptionFactory;
import io.coala.time.Scheduler;
import nl.rivm.cib.episim.model.Individual;
import nl.rivm.cib.episim.model.TransitionEvent;
import nl.rivm.cib.episim.model.disease.Condition;
import nl.rivm.cib.episim.model.disease.SymptomPhase;
import nl.rivm.cib.episim.model.disease.TreatmentStage;
import nl.rivm.cib.episim.model.disease.SymptomPhase.SymptomEvent;
import nl.rivm.cib.episim.model.disease.TreatmentStage.TreatmentEvent;
import nl.rivm.cib.episim.model.disease.infection.EpidemicCompartment;
import nl.rivm.cib.episim.model.disease.infection.Infection;
import nl.rivm.cib.episim.model.disease.infection.EpidemicCompartment.CompartmentEvent;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * {@link SimpleCondition} implements {@link Condition}
 * 
 * @version $Id: 0d19d6801cb9fefc090fe46d46fc23c2d3afc275 $
 * @author Rick van Krevelen
 */
public class SimpleCondition implements Condition
{

	/**
	 * @param individual the {@link Individual}
	 * @param infection the {@link Infection}
	 * @return a {@link SimpleCondition}
	 */
	public static SimpleCondition of( final Individual individual,
		final Infection infection )
	{
		return of( individual, infection,
				EpidemicCompartment.Simple.SUSCEPTIBLE,
				SymptomPhase.ASYMPTOMATIC, TreatmentStage.UNTREATED );
	}

	/**
	 * @param individual the {@link Individual}
	 * @param infection the {@link Infection}
	 * @param compartment the {@link EpidemicCompartment}
	 * @param symptoms the {@link SymptomPhase}
	 * @param treatment the {@link TreatmentStage}
	 * @return a {@link SimpleCondition}
	 */
	public static SimpleCondition of( final Individual individual,
		final Infection infection, final EpidemicCompartment compartment,
		final SymptomPhase symptoms, final TreatmentStage treatment )
	{
		return new SimpleCondition( individual, infection, compartment,
				symptoms, treatment );
	}

	private final Subject<TransitionEvent<?>, TransitionEvent<?>> transitions = PublishSubject
			.create();

	private final Individual individual;

	private final Infection infection;

	private EpidemicCompartment compartment;

	private SymptomPhase symptoms;

	private TreatmentStage treatment;

	/**
	 * {@link SimpleCondition} constructor
	 * 
	 * @param individual the {@link Individual}
	 * @param infection the {@link Infection}
	 * @param compartment the {@link EpidemicCompartment}
	 * @param symptoms the {@link SymptomPhase}
	 * @param treatment the {@link TreatmentStage}
	 */
	public SimpleCondition( final Individual individual,
		final Infection infection, final EpidemicCompartment compartment,
		final SymptomPhase symptoms, final TreatmentStage treatment )
	{
		Objects.requireNonNull( infection );
		this.individual = individual;
		this.infection = infection;
		this.compartment = compartment;
		this.symptoms = symptoms;
		this.treatment = treatment;
	}

	protected void setCompartment( final EpidemicCompartment compartment )
	{
		this.transitions.onNext( CompartmentEvent.of( this, compartment ) );
		this.compartment = compartment;
	}

	protected void setTreatmentStage( final TreatmentStage treatment )
	{
		this.transitions.onNext( TreatmentEvent.of( this, treatment ) );
		this.treatment = treatment;
	}

	protected void setSymptomPhase( final SymptomPhase symptoms )
	{
		this.transitions.onNext( SymptomEvent.of( this, symptoms ) );
		this.symptoms = symptoms;
	}

	@Override
	public Individual host()
	{
		return this.individual;
	}

	@Override
	public Scheduler scheduler()
	{
		return host().scheduler();
	}

	@Override
	public Infection disease()
	{
		return this.infection;
	}

	@Override
	public EpidemicCompartment getCompartment()
	{
		return this.compartment;
	}

	@Override
	public TreatmentStage getTreatmentStage()
	{
		return this.treatment;
	}

	@Override
	public SymptomPhase getSymptomPhase()
	{
		return this.symptoms;
	}

	@Override
	public Observable<TransitionEvent<?>> transitions()
	{
		return this.transitions.asObservable();
	}

	@Override
	public void infect()
	{
		if( !getCompartment().isSusceptible() )
			throw ExceptionFactory.createUnchecked(
					"Can't become exposed when: {}", getCompartment() );

		setCompartment( EpidemicCompartment.Simple.EXPOSED );

		after( disease().drawLatentPeriod() )
				.call( this::setCompartment,
						EpidemicCompartment.Simple.INFECTIVE )
				.thenAfter( disease().drawRecoverPeriod() )
				.call( this::setCompartment,
						EpidemicCompartment.Simple.RECOVERED )
				.thenAfter( disease().drawWanePeriod() )
				.call( this::setCompartment,
						EpidemicCompartment.Simple.SUSCEPTIBLE );
		after( disease().drawOnsetPeriod() )
				.call( this::setSymptomPhase, SymptomPhase.SYSTEMIC )
				.thenAfter( disease().drawSymptomPeriod() )
				.call( this::setSymptomPhase, SymptomPhase.ASYMPTOMATIC );
	}
}