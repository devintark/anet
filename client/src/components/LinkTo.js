import React, {Component} from 'react'
import {Link} from 'react-router'
import utils from 'utils'

import * as Models from 'models'

const MODEL_NAMES = Object.keys(Models).map(key => {
	let camel = utils.camelCase(key)
	Models[camel] = Models[key]
	return camel
})

export default class LinkTo extends Component {
	static propTypes = {
		componentClass: React.PropTypes.oneOfType([
			React.PropTypes.string,
			React.PropTypes.func,
		]),
	}

	static defaultProps = {
		componentClass: Link
	}

	render() {
		let {componentClass: Component, children, ...componentProps} = this.props

		let modelName = Object.keys(componentProps).find(key => MODEL_NAMES.indexOf(key) !== -1)
		if (!modelName) {
			console.error("You called LinkTo without passing a Model as a prop")
			return null
		}

		let modelInstance = this.props[modelName]
		if (!modelInstance)
			return null

		let modelClass = Models[modelName]
		let to = modelClass.pathFor(modelInstance)
		componentProps = Object.without(componentProps, modelName)

		return <Component to={to} {...componentProps}>
			{children || modelClass.prototype.toString.call(modelInstance)}
		</Component>
	}
}

MODEL_NAMES.forEach(key => LinkTo.propTypes[key] = React.PropTypes.oneOfType([
	React.PropTypes.instanceOf(Models[key]),
	React.PropTypes.object,
]))