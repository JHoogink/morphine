package nl.rivm.cib.morphine.attitude;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.coala.enterprise.Actor;
import io.coala.json.JsonUtil;
import io.coala.math.DecimalUtil;
import io.coala.util.Compare;
import nl.rivm.cib.epidemes.model.VaxHesitancy;
import nl.rivm.cib.epidemes.model.VaxOccasion;

/**
 * {@link SimpleWeightedAverager} averages own default {@link VaxPosition}
 * and all those observed latest per source {@link Actor.ID}, filtered by
 * their current reputation. In theory one could set the weight for their
 * own {@link VaxPosition} to 0 by giving oneself a reputation below the
 * (inverse) calculation threshold, effectively ignoring one's own position.
 * However, if all relevant positions' weights sum to 0, then the default
 * position carries all the weight.
 */
class SimpleWeightedAverager implements VaxHesitancy
{
	public static SimpleWeightedAverager of( final Number myConfidence,
		final Number myComplacency, final Number myCalculation )
	{
		return of( myConfidence, myComplacency, myCalculation,
				id -> BigDecimal.ONE );
	}

	public static SimpleWeightedAverager of( final Number myConfidence,
		final Number myComplacency, final Number myCalculation,
		final Function<Actor.ID, Number> appreciator )
	{
		return of( myConfidence, myComplacency, myCalculation, appreciator,
				occ -> Compare.min( occ.utility(), occ.proximity(),
						occ.clarity(), occ.affinity() ) );
	}

	public static SimpleWeightedAverager of( final Number myConfidence,
		final Number myComplacency, final Number myCalculation,
		final Function<Actor.ID, Number> appreciator,
		final Function<VaxOccasion, Number> evaluator )
	{
		return new SimpleWeightedAverager( myConfidence, myComplacency,
				myCalculation, appreciator, evaluator );
	}

	private final Function<Actor.ID, BigDecimal> appreciator;

	private final Function<VaxOccasion, BigDecimal> evaluator;

	/** (dynamic) argument "memory" of an individual */
	@JsonProperty
	private final Map<Actor.ID, BigDecimal[]> positions = new HashMap<>();

	@JsonProperty
	private final BigDecimal[] myDefault;

	private transient BigDecimal[] myPosition = null;

	private BigDecimal calculation;

	public SimpleWeightedAverager( final Number myConfidence,
		final Number myComplacency, final Number myCalculation,
		final Function<Actor.ID, Number> appreciator,
		final Function<VaxOccasion, Number> evaluator )
	{
		setCalculation( myCalculation );
		this.appreciator = id -> DecimalUtil
				.valueOf( appreciator.apply( id ) );
		this.evaluator = occ -> DecimalUtil
				.valueOf( evaluator.apply( occ ) );
		this.myDefault = toPosition( myConfidence, myComplacency );
		reset();
	}

	@Override
	public String toString()
	{
		return JsonUtil.stringify( this );
	}

	@Override
	public void setCalculation( final Number calculation )
	{
		if( calculation == this.calculation ) return;
		this.calculation = DecimalUtil.valueOf( calculation );
		reset();
	}

	@Override
	public BigDecimal getCalculation()
	{
		return this.calculation;
	}

	public void observe( final Actor.ID ref, final Number confidence,
		final Number complacency )
	{
		this.positions.put( ref, toPosition( confidence, complacency ) );
		reset();
	}

	public void observeRisk( final Actor.ID sourceRef,
		final Number vaccineRisk, final Number diseaseRisk )
	{
		observe( sourceRef,
				BigDecimal.ONE
						.subtract( DecimalUtil.valueOf( vaccineRisk ) ),
				BigDecimal.ONE
						.subtract( DecimalUtil.valueOf( diseaseRisk ) ) );
	}

//	@Override
//	public BigDecimal getAppreciation( final Actor.ID sourceRef )
//	{
//		return this.appreciator.apply( sourceRef );
//	}

	@Override
	@JsonIgnore
	public BigDecimal getComplacency()
	{
		return myPosition()[SocialFactors.COMPLACENCY.ordinal()];
	}

	@Override
	@JsonIgnore
	public BigDecimal getConfidence()
	{
		return myPosition()[SocialFactors.CONFIDENCE.ordinal()];
	}

	private BigDecimal[] toPosition( final Number confidence,
		final Number complacency )
	{
		return new BigDecimal[] { DecimalUtil.valueOf( confidence ),
				DecimalUtil.valueOf( complacency ) };
	}

	private void reset()
	{
		this.myPosition = null;
	}

	/**
	 * @param sums
	 * @param id
	 * @param augend
	 * @return the weight to allow map/reduce
	 */
	private BigDecimal weightedAddition( final BigDecimal[] sums,
		final Actor.ID id, final BigDecimal[] augend )
	{
		final BigDecimal weight = VaxHesitancy.shiftedAppreciation(
				this.appreciator.apply( id ), getCalculation() );
		if( weight.signum() != 0 ) for( int i = 0; i < sums.length; i++ )
			sums[i] = sums[i].add( augend[i].multiply( weight ) );
		return weight;
	}

	/**
	 * if necessary, compute attitude from default + filtered (dynamic)
	 * "memory"
	 * 
	 * @return the current position
	 */
	private BigDecimal[] myPosition()
	{
		if( this.myPosition != null ) return this.myPosition;

		// start from default position
		final int len = this.myDefault.length;

		// initialize at default position, applying weight accordingly
		final BigDecimal[] sums = new BigDecimal[] { BigDecimal.ZERO,
				BigDecimal.ZERO };
		final BigDecimal w = weightedAddition( sums, null, this.myDefault )
				.add( this.positions.entrySet().parallelStream()
						.map( entry -> weightedAddition( sums,
								entry.getKey(), entry.getValue() ) )
						.reduce( BigDecimal::add )
						.orElse( BigDecimal.ZERO ) );

		this.myPosition = new BigDecimal[len];
		if( BigDecimal.ZERO.equals( w ) ) // zero weights: assume default
			System.arraycopy( this.myDefault, 0, this.myPosition, 0, len );
		else // final division for the weighted average
			for( int i = 0; i < sums.length; i++ )
				this.myPosition[i] = DecimalUtil.divide( sums[i], w );
		return this.myPosition;
	}

	@Override
	public BigDecimal getConvenience( VaxOccasion occ )
	{
		return this.evaluator.apply( occ );
	}

	@Override
	public boolean isPositive( final VaxOccasion occ )
	{
		return Compare.gt( getConfidence().subtract( getComplacency() ),
				getConvenience( occ ) );
	}
}